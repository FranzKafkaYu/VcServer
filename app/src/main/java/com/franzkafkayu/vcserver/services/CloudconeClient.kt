package com.franzkafkayu.vcserver.services

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class CloudconeInstance(
	val id: String,
	val address: String?,
	val powerStatus: String?
)

data class CloudconeInstanceInfo(
	val hostname: String? = null,
	val ipAddresses: List<String> = emptyList(),
	val os: String? = null,
	val cpu: String? = null,
	val ram: String? = null,
	val disk: String? = null,
	val bandwidthUsed: String? = null,
	val bandwidthTotal: String? = null,
	val node: String? = null,
	val powerStatus: String? = null
) {
	fun isEmpty(): Boolean {
		return hostname == null &&
			ipAddresses.isEmpty() &&
			os == null &&
			cpu == null &&
			ram == null &&
			disk == null &&
			bandwidthUsed == null &&
			bandwidthTotal == null &&
			node == null &&
			powerStatus == null
	}
}

data class CloudconeCallResult(
	val ok: Boolean,
	val message: String?,
	val instances: List<CloudconeInstance>,
	val info: CloudconeInstanceInfo? = null
)

internal fun parseCloudconeBody(json: String): CloudconeCallResult {
	if (json.isBlank()) {
		return CloudconeCallResult(ok = false, message = "empty response", instances = emptyList())
	}
	val element = JsonParser.parseString(json)
	if (element.isJsonArray) {
		return CloudconeCallResult(ok = true, message = null, instances = instancesFrom(element.asJsonArray))
	}
	if (!element.isJsonObject) {
		return CloudconeCallResult(ok = false, message = "invalid response", instances = emptyList())
	}
	val obj = element.asJsonObject
	val instances = extractInstances(obj)
	val message = textField(obj, "message")
	if (isExplicitFailure(obj) && instances.isEmpty()) {
		return CloudconeCallResult(ok = false, message = message ?: "request failed", instances = emptyList())
	}
	val info = extractInfo(obj)?.takeUnless { it.isEmpty() }
		?: instances.firstOrNull()?.let { instance ->
			CloudconeInstanceInfo(
				hostname = null,
				ipAddresses = listOfNotNull(instance.address),
				powerStatus = instance.powerStatus
			).takeUnless { it.isEmpty() }
		}
	return CloudconeCallResult(ok = true, message = message, instances = instances, info = info)
}

private fun extractInfo(obj: JsonObject): CloudconeInstanceInfo? {
	val candidates = unwrapInfoObjects(obj)
	for (candidate in candidates) {
		val info = instanceInfoFrom(candidate)
		if (!info.isEmpty()) return info
	}
	return null
}

private fun unwrapInfoObjects(obj: JsonObject): List<JsonObject> {
	val found = mutableListOf<JsonObject>()
	fun add(element: JsonElement?) {
		when {
			element == null || element.isJsonNull -> Unit
			element.isJsonObject -> {
				val nested = element.asJsonObject
				found += nested
				for (name in listOf("instance", "compute", "server", "vps", "information", "details")) {
					ci(nested, name)?.let { add(it) }
				}
				val instancesField = ci(nested, "instances")
				when {
					instancesField == null -> Unit
					instancesField.isJsonArray -> instancesField.asJsonArray.forEach { item ->
						if (item.isJsonObject) found += item.asJsonObject
					}
					instancesField.isJsonObject -> add(instancesField)
				}
			}
			element.isJsonArray && element.asJsonArray.size() > 0 -> {
				val first = element.asJsonArray[0]
				if (first.isJsonObject) add(first)
			}
			element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
				val text = element.asString.trim()
				if (text.startsWith("{") || text.startsWith("[")) {
					runCatching { add(JsonParser.parseString(text)) }
				}
			}
		}
	}
	add(ci(obj, "data"))
	add(ci(obj, "__data"))
	found += obj
	return found
}

private fun instanceInfoFrom(obj: JsonObject): CloudconeInstanceInfo {
	val bandwidth = ci(obj, "bandwidth")?.takeIf { it.isJsonObject }?.asJsonObject
	val diskObject = ci(obj, "disk")?.takeIf { it.isJsonObject }?.asJsonObject
	return CloudconeInstanceInfo(
		hostname = firstText(obj, listOf("hostname", "label")),
		ipAddresses = ipAddressesOf(obj),
		os = namedOrText(obj, "os") ?: firstText(obj, listOf("template", "distro", "operating_system")),
		cpu = firstText(obj, listOf("cpu", "cpus", "cores", "vcpu", "cpu_cores")),
		ram = firstText(obj, listOf("ram", "memory", "ram_mb", "memory_mb")),
		disk = diskObject?.let { firstText(it, listOf("total", "size", "used")) }
			?: firstText(obj, listOf("disk", "hdd", "storage", "disk_gb", "disk_size")),
		bandwidthUsed = bandwidth?.let { firstText(it, listOf("used", "used_gb", "usage")) }
			?: firstText(obj, listOf("bw_used", "bandwidth_used")),
		bandwidthTotal = bandwidth?.let { firstText(it, listOf("total", "limit", "allowed")) }
			?: firstText(obj, listOf("bw_limit", "bandwidth_total")),
		node = locationText(obj)
			?: firstText(obj, listOf("nodename", "node_name"))
			?: namedOrText(obj, "node")?.takeUnless { value -> value.all { it.isDigit() } },
		powerStatus = powerOf(obj)
	)
}

private fun locationText(obj: JsonObject): String? {
	val location = ci(obj, "location")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	return firstText(location, listOf("location_nice_name", "region_name", "location_name", "name"))
}

private fun ipAddressesOf(obj: JsonObject): List<String> {
	val collected = mutableListOf<String>()
	firstText(obj, listOf("ip", "mainip", "ipv4", "primary_ip", "ip_address"))?.let { collected += it }
	val ips = ci(obj, "ips") ?: ci(obj, "ip_addresses")
	when {
		ips == null -> Unit
		ips.isJsonPrimitive -> splitIps(primitiveText(ips)).forEach { collected += it }
		ips.isJsonArray -> ips.asJsonArray.forEach { item ->
			if (item.isJsonPrimitive) splitIps(primitiveText(item)).forEach { collected += it }
			if (item.isJsonObject) {
				firstText(item.asJsonObject, listOf("ip", "address"))?.let { collected += it }
			}
		}
	}
	return collected.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

private fun splitIps(raw: String): List<String> {
	return raw.split(',', ' ', ';').map { it.trim() }.filter { it.isNotBlank() }
}

private fun namedOrText(obj: JsonObject, name: String): String? {
	val element = ci(obj, name) ?: return null
	if (element.isJsonObject) {
		return firstText(element.asJsonObject, listOf("name", "hostname", "title", "label"))
	}
	return primitiveText(element).takeIf { it.isNotBlank() }
}

private fun firstText(obj: JsonObject, names: List<String>): String? {
	for (name in names) {
		val element = ci(obj, name) ?: continue
		if (element.isJsonObject) {
			firstText(element.asJsonObject, listOf("name", "hostname", "title", "label"))?.let { return it }
			continue
		}
		if (element.isJsonArray) continue
		primitiveText(element).takeIf { it.isNotBlank() }?.let { return it }
	}
	return null
}

private fun ci(obj: JsonObject, name: String): JsonElement? {
	obj.get(name)?.let { return it }
	return obj.entrySet().firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

private fun primitiveText(element: JsonElement): String {
	if (!element.isJsonPrimitive) return ""
	val primitive = element.asJsonPrimitive
	return when {
		primitive.isString -> primitive.asString
		else -> primitive.asString
	}
}

private fun extractInstances(obj: JsonObject): List<CloudconeInstance> {
	arrayField(obj, "instances")?.let { return instancesFrom(it) }
	val data = ci(obj, "data")
	if (data != null && data.isJsonArray) return instancesFrom(data.asJsonArray)
	val wrapped = ci(obj, "__data")
	if (wrapped != null && wrapped.isJsonArray) return instancesFrom(wrapped.asJsonArray)
	if (wrapped != null && wrapped.isJsonObject) {
		arrayField(wrapped.asJsonObject, "instances")?.let { return instancesFrom(it) }
		instanceFrom(wrapped.asJsonObject)?.let { return listOf(it) }
	}
	instanceFrom(obj)?.let { return listOf(it) }
	return emptyList()
}

private fun instancesFrom(array: JsonArray): List<CloudconeInstance> {
	return array.mapNotNull { element ->
		if (element.isJsonObject) instanceFrom(element.asJsonObject) else null
	}
}

private fun instanceFrom(obj: JsonObject): CloudconeInstance? {
	val idElement = ci(obj, "id") ?: return null
	if (!idElement.isJsonPrimitive) return null
	val id = primitiveText(idElement)
	if (id.isBlank()) return null
	return CloudconeInstance(
		id = id,
		address = addressOf(obj),
		powerStatus = powerOf(obj)
	)
}

private fun addressOf(obj: JsonObject): String? {
	return ipAddressesOf(obj).firstOrNull()
}

private fun powerOf(obj: JsonObject): String? {
	val state = ci(obj, "state")
	if (state != null && state.isJsonObject) {
		firstText(state.asJsonObject, listOf("name", "label", "status"))?.let { return it }
	}
	for (name in listOf("power_status", "power", "state", "ve_status")) {
		val element = ci(obj, name) ?: continue
		primitiveText(element).takeIf { it.isNotBlank() }?.let { return it }
	}
	val status = ci(obj, "status")
	if (status != null && status.isJsonPrimitive && status.asJsonPrimitive.isString) {
		return status.asString.takeIf { it.isNotBlank() }
	}
	return null
}

private fun isExplicitFailure(obj: JsonObject): Boolean {
	val status = obj.get("status")
	if (status != null && status.isJsonPrimitive && status.asJsonPrimitive.isNumber && status.asInt == 0) return true
	val error = obj.get("error")
	if (error != null && error.isJsonPrimitive && error.asJsonPrimitive.isNumber && error.asInt != 0) return true
	return false
}

private fun arrayField(obj: JsonObject, name: String): JsonArray? {
	val element = ci(obj, name) ?: return null
	return if (element.isJsonArray) element.asJsonArray else null
}

private fun textField(obj: JsonObject, name: String): String? {
	val element: JsonElement = obj.get(name) ?: return null
	if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
	return element.asString.takeIf { it.isNotBlank() }
}

interface CloudconeClient {
	suspend fun list(apiKey: String, hash: String): CloudconeCallResult
	suspend fun information(apiKey: String, hash: String, instanceId: String): CloudconeCallResult
	suspend fun power(apiKey: String, hash: String, instanceId: String, action: String): CloudconeCallResult
}

class HttpCloudconeClient : CloudconeClient {
	override suspend fun list(apiKey: String, hash: String): CloudconeCallResult {
		return request("GET", PATH_LIST, apiKey, hash)
	}

	override suspend fun information(apiKey: String, hash: String, instanceId: String): CloudconeCallResult {
		val id = instanceId.trim()
		Log.i(TAG, "information instanceId=$id")
		return request("GET", "compute/$id/information", apiKey, hash)
	}

	override suspend fun power(
		apiKey: String,
		hash: String,
		instanceId: String,
		action: String
	): CloudconeCallResult {
		return request("POST", "compute/$instanceId/$action", apiKey, hash)
	}

	private suspend fun request(
		method: String,
		path: String,
		apiKey: String,
		hash: String
	): CloudconeCallResult = withContext(Dispatchers.IO) {
		var url = URL("$API_ROOT$path")
		repeat(6) { redirects ->
			val connection = (url.openConnection() as HttpURLConnection).apply {
				requestMethod = method
				instanceFollowRedirects = false
				connectTimeout = TIMEOUT_MS
				readTimeout = TIMEOUT_MS
				setRequestProperty("Accept", "application/json")
				setRequestProperty("App-Secret", apiKey)
				setRequestProperty("Hash", hash)
				if (method == "POST") {
					doOutput = true
					setFixedLengthStreamingMode(0)
				}
			}
			try {
				val code = connection.responseCode
				if (code in 300..399 && redirects < 5) {
					val location = connection.getHeaderField("Location")
					if (location.isNullOrBlank()) {
						return@withContext CloudconeCallResult(ok = false, message = "http $code", instances = emptyList())
					}
					Log.i(TAG, "redirect path=$path http=$code to=$location")
					url = URL(url, location)
					return@repeat
				}
				val stream = if (code in 200..299) connection.inputStream else connection.errorStream
				val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
				Log.i(TAG, "path=$path url=${url.path} http=$code bytes=${text.length}")
				val parsed = try {
					if (text.isBlank()) {
						CloudconeCallResult(
							ok = false,
							message = "empty response http $code",
							instances = emptyList()
						)
					} else {
						parseCloudconeBody(text)
					}
				} catch (e: Exception) {
					CloudconeCallResult(ok = false, message = "http $code", instances = emptyList())
				}
				if (path.contains("information") && parsed.info == null) {
					val keys = runCatching {
						JsonParser.parseString(text).asJsonObject.keySet().joinToString()
					}.getOrDefault("not-json")
					Log.w(TAG, "empty info path=$path keys=$keys")
					if (parsed.ok) {
						return@withContext parsed.copy(ok = false, message = "empty info keys=$keys")
					}
				}
				return@withContext if (code !in 200..299) {
					parsed.copy(ok = false, message = parsed.message ?: "http $code")
				} else {
					parsed
				}
			} finally {
				connection.disconnect()
			}
		}
		CloudconeCallResult(ok = false, message = "too many redirects", instances = emptyList())
	}

	companion object {
		private const val TAG = "CloudconeClient"
		private const val API_ROOT = "https://api.cloudcone.com/api/v2/"
		private const val TIMEOUT_MS = 20_000
		const val PATH_LIST = "compute/instances"
		const val ACTION_BOOT = "boot"
		const val ACTION_SHUTDOWN = "shutdown"
		const val ACTION_REBOOT = "reboot"
	}
}
