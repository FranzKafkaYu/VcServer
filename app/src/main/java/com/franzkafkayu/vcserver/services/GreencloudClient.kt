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

data class GreencloudInstance(
	val id: String,
	val address: String?,
	val powerStatus: String?,
	val label: String?
)

data class GreencloudInstanceInfo(
	val name: String? = null,
	val hostname: String? = null,
	val ipAddresses: List<String> = emptyList(),
	val cpu: String? = null,
	val ram: String? = null,
	val disk: String? = null,
	val powerStatus: String? = null,
	val trafficUsed: String? = null,
	val trafficLimit: String? = null,
	val rescue: Boolean? = null,
	val suspended: Boolean? = null,
	val vncEnabled: Boolean? = null
) {
	fun isEmpty(): Boolean {
		return name == null &&
			hostname == null &&
			ipAddresses.isEmpty() &&
			cpu == null &&
			ram == null &&
			disk == null &&
			powerStatus == null &&
			trafficUsed == null &&
			trafficLimit == null &&
			rescue == null &&
			suspended == null &&
			vncEnabled == null
	}
}

data class GreencloudCallResult(
	val ok: Boolean,
	val message: String?,
	val instances: List<GreencloudInstance>,
	val powerStatus: String? = null,
	val info: GreencloudInstanceInfo? = null,
	val expectedPassword: String? = null
)

internal fun parseGreencloudBody(json: String, httpCode: Int = 200): GreencloudCallResult {
	if (httpCode == 401) {
		return GreencloudCallResult(ok = false, message = "unauthorized", instances = emptyList())
	}
	if (httpCode !in 200..299 && json.isBlank()) {
		return GreencloudCallResult(ok = false, message = "http $httpCode", instances = emptyList())
	}
	if (json.isBlank()) {
		return if (httpCode in 200..299) {
			GreencloudCallResult(ok = true, message = null, instances = emptyList())
		} else {
			GreencloudCallResult(ok = false, message = "empty response", instances = emptyList())
		}
	}
	val element = try {
		JsonParser.parseString(json)
	} catch (_: Exception) {
		return GreencloudCallResult(ok = false, message = "invalid response", instances = emptyList())
	}
	if (element.isJsonArray) {
		return GreencloudCallResult(
			ok = httpCode in 200..299,
			message = null,
			instances = instancesFrom(element.asJsonArray)
		)
	}
	if (!element.isJsonObject) {
		return GreencloudCallResult(ok = false, message = "invalid response", instances = emptyList())
	}
	val obj = element.asJsonObject
	val message = firstError(obj) ?: textField(obj, "message") ?: textField(obj, "msg")
	if (httpCode !in 200..299) {
		return GreencloudCallResult(ok = false, message = message ?: "http $httpCode", instances = emptyList())
	}
	if (isFailure(obj) && extractInstances(obj).isEmpty()) {
		return GreencloudCallResult(ok = false, message = message ?: "request failed", instances = emptyList())
	}
	val instances = extractInstances(obj)
	val info = extractInfo(obj)
	val power = powerFrom(obj) ?: info?.powerStatus ?: instances.firstOrNull()?.powerStatus
	return GreencloudCallResult(
		ok = true,
		message = message,
		instances = instances,
		powerStatus = power,
		info = info,
		expectedPassword = expectedPassword(obj)
	)
}

private fun isFailure(obj: JsonObject): Boolean {
	if (obj.has("errors")) return true
	val success = obj.get("success") ?: return false
	if (success.isJsonPrimitive) {
		val p = success.asJsonPrimitive
		if (p.isBoolean) return !p.asBoolean
		if (p.isString) return p.asString.equals("false", ignoreCase = true) || p.asString == "0"
		if (p.isNumber) return p.asInt == 0
	}
	return obj.has("error")
}

private fun firstError(obj: JsonObject): String? {
	val error = obj.get("error") ?: obj.get("errors") ?: return null
	when {
		error.isJsonPrimitive -> return error.asString.takeIf { it.isNotBlank() }
		error.isJsonArray -> error.asJsonArray.firstOrNull { it.isJsonPrimitive }?.asString?.let { return it }
		error.isJsonObject -> return error.asJsonObject.entrySet().firstOrNull()?.value?.asString
	}
	return null
}

private fun extractInstances(obj: JsonObject): List<GreencloudInstance> {
	val data = named(obj, "data")
	when {
		data == null -> Unit
		data.isJsonArray -> return instancesFrom(data.asJsonArray)
		data.isJsonObject -> {
			val nested = data.asJsonObject
			arrayField(nested, "data")?.let { return instancesFrom(it) }
			instanceFrom(nested)?.let { return listOf(it) }
		}
	}
	for (name in listOf("servers", "instances", "accounts", "services", "vps")) {
		val field = named(obj, name) ?: continue
		when {
			field.isJsonArray -> return instancesFrom(field.asJsonArray)
			field.isJsonObject -> instanceFrom(field.asJsonObject)?.let { return listOf(it) }
		}
	}
	instanceFrom(obj)?.let { return listOf(it) }
	return emptyList()
}

private fun instancesFrom(array: JsonArray): List<GreencloudInstance> {
	return array.mapNotNull { el -> if (el.isJsonObject) instanceFrom(el.asJsonObject) else null }
}

private fun instanceFrom(obj: JsonObject): GreencloudInstance? {
	val id = firstText(obj, listOf("id", "uuid", "serverId", "server_id")) ?: return null
	if (id.isBlank()) return null
	return GreencloudInstance(
		id = id,
		address = ipv4From(obj) ?: firstText(obj, listOf("hostname", "ip", "mainip")),
		powerStatus = powerFrom(obj),
		label = firstText(obj, listOf("name", "hostname", "label"))
	)
}

private fun powerFrom(obj: JsonObject): String? {
	val state = named(obj, "state")
	if (state != null && state.isJsonObject) {
		val nested = state.asJsonObject
		firstText(nested, listOf("status", "state"))?.let { return it }
		val running = nested.get("running")
		if (running != null && running.isJsonPrimitive && running.asJsonPrimitive.isBoolean) {
			return if (running.asBoolean) "running" else "stopped"
		}
	}
	named(obj, "data")?.takeIf { it.isJsonObject }?.asJsonObject?.let { inner ->
		if (inner !== obj) powerFrom(inner)?.let { return it }
	}
	return firstText(obj, listOf("status", "state", "power", "power_status"))
}

private fun extractInfo(obj: JsonObject): GreencloudInstanceInfo? {
	val source = named(obj, "data")?.takeIf { it.isJsonObject }?.asJsonObject ?: obj
	if (!source.has("id") && !source.has("name") && !source.has("network")) return null
	val info = infoFrom(source)
	return if (info.isEmpty()) null else info
}

private fun infoFrom(obj: JsonObject): GreencloudInstanceInfo {
	val ips = allIpv4(obj)
	val disk = storageCapacity(obj)
	return GreencloudInstanceInfo(
		name = firstText(obj, listOf("name")),
		hostname = firstText(obj, listOf("hostname")),
		ipAddresses = ips,
		cpu = firstText(obj, listOf("cpu")),
		ram = firstText(obj, listOf("memory")),
		disk = disk,
		powerStatus = powerFrom(obj),
		trafficUsed = trafficUsed(obj),
		trafficLimit = trafficLimit(obj),
		rescue = boolField(obj, "rescue"),
		suspended = boolField(obj, "suspended"),
		vncEnabled = boolField(obj, "vncEnabled")
	)
}

private fun expectedPassword(obj: JsonObject): String? {
	textField(obj, "expectedPassword")?.let { return it }
	val data = named(obj, "data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	return textField(data, "expectedPassword")
}

private fun ipv4From(obj: JsonObject): String? = allIpv4(obj).firstOrNull()

private fun allIpv4(obj: JsonObject): List<String> {
	val network = named(obj, "network")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
	val out = mutableListOf<String>()
	collectIpv4(named(network, "primary"), out)
	collectIpv4(named(network, "secondary"), out)
	return out
}

private fun collectIpv4(node: JsonElement?, out: MutableList<String>) {
	if (node == null || !node.isJsonObject) return
	val ipv4 = named(node.asJsonObject, "ipv4") ?: return
	when {
		ipv4.isJsonPrimitive -> ipv4.asString.takeIf { it.isNotBlank() }?.let { out += it }
		ipv4.isJsonArray -> ipv4.asJsonArray.forEach { el ->
			when {
				el.isJsonPrimitive -> el.asString.takeIf { it.isNotBlank() }?.let { out += it }
				el.isJsonObject -> firstText(el.asJsonObject, listOf("address", "ip", "ipv4"))?.let { out += it }
			}
		}
	}
}

private fun storageCapacity(obj: JsonObject): String? {
	val storage = named(obj, "storage") ?: return null
	if (!storage.isJsonArray) return null
	val disks = storage.asJsonArray.mapNotNull { el -> if (el.isJsonObject) el.asJsonObject else null }
	val primary = disks.firstOrNull { boolField(it, "primary") == true } ?: disks.firstOrNull()
	return primary?.let { firstText(it, listOf("capacity")) }
}

private fun trafficUsed(obj: JsonObject): String? {
	val state = named(obj, "state")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	val network = named(state, "network")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	val primary = named(network, "primary")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	val traffic = named(primary, "traffic")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	val total = numberField(traffic, "total") ?: return null
	return formatBytes(total)
}

private fun trafficLimit(obj: JsonObject): String? {
	val network = named(obj, "network")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	val primary = named(network, "primary")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
	return firstText(primary, listOf("limit"))
}

private fun boolField(obj: JsonObject, name: String): Boolean? {
	val el = named(obj, name) ?: return null
	if (!el.isJsonPrimitive) return null
	val p = el.asJsonPrimitive
	return when {
		p.isBoolean -> p.asBoolean
		p.isNumber -> p.asInt != 0
		p.isString -> p.asString.equals("true", ignoreCase = true) || p.asString == "1"
		else -> null
	}
}

private fun numberField(obj: JsonObject, name: String): Long? {
	val el = named(obj, name) ?: return null
	if (!el.isJsonPrimitive) return null
	val p = el.asJsonPrimitive
	return when {
		p.isNumber -> p.asLong
		p.isString -> p.asString.toLongOrNull()
		else -> null
	}
}

private fun formatBytes(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	val kb = bytes / 1024.0
	if (kb < 1024) return String.format("%.1f KB", kb)
	val mb = kb / 1024.0
	if (mb < 1024) return String.format("%.1f MB", mb)
	return String.format("%.2f GB", mb / 1024.0)
}

private fun named(obj: JsonObject, name: String): JsonElement? {
	obj.get(name)?.let { return it }
	return obj.entrySet().firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

private fun arrayField(obj: JsonObject, name: String): JsonArray? {
	val el = named(obj, name) ?: return null
	return if (el.isJsonArray) el.asJsonArray else null
}

private fun textField(obj: JsonObject, name: String): String? {
	val el = named(obj, name) ?: return null
	if (!el.isJsonPrimitive) return null
	return el.asString.takeIf { it.isNotBlank() }
}

private fun firstText(obj: JsonObject, names: List<String>): String? {
	for (name in names) {
		textField(obj, name)?.let { return it }
	}
	return null
}

interface GreencloudClient {
	suspend fun list(apiToken: String): GreencloudCallResult
	suspend fun power(apiToken: String, instanceId: String, action: GreencloudPowerAction): GreencloudCallResult
	suspend fun status(apiToken: String, instanceId: String): GreencloudCallResult
	suspend fun details(apiToken: String, instanceId: String): GreencloudCallResult
	suspend fun resetPassword(apiToken: String, instanceId: String): GreencloudCallResult
}

class HttpGreencloudClient : GreencloudClient {
	override suspend fun list(apiToken: String): GreencloudCallResult {
		return request(apiToken, "GET", "/server?results=200")
	}

	override suspend fun power(
		apiToken: String,
		instanceId: String,
		action: GreencloudPowerAction
	): GreencloudCallResult {
		val path = when (action) {
			GreencloudPowerAction.START -> "/server/$instanceId/boot"
			GreencloudPowerAction.STOP -> "/server/$instanceId/shutdown"
			GreencloudPowerAction.RESTART -> "/server/$instanceId/restart"
		}
		return request(apiToken, "POST", path)
	}

	override suspend fun status(apiToken: String, instanceId: String): GreencloudCallResult {
		return request(apiToken, "GET", "/server/$instanceId?state=true")
	}

	override suspend fun details(apiToken: String, instanceId: String): GreencloudCallResult {
		return request(apiToken, "GET", "/server/$instanceId?state=true")
	}

	override suspend fun resetPassword(apiToken: String, instanceId: String): GreencloudCallResult {
		return request(apiToken, "POST", "/server/$instanceId/resetPassword")
	}

	private suspend fun request(
		apiToken: String,
		method: String,
		path: String
	): GreencloudCallResult = withContext(Dispatchers.IO) {
		val url = URL(API_ROOT + path)
		val connection = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = method
			connectTimeout = TIMEOUT_MS
			readTimeout = TIMEOUT_MS
			setRequestProperty("Accept", "application/json")
			setRequestProperty("Authorization", "Bearer ${apiToken.trim()}")
			if (method == "POST") {
				doOutput = true
				setRequestProperty("Content-Type", "application/json")
				setFixedLengthStreamingMode(0)
			}
		}
		try {
			if (method == "POST") {
				connection.outputStream.use { }
			}
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
			Log.i(TAG, "method=$method path=$path http=$code bytes=${text.length}")
			parseGreencloudBody(text, code)
		} catch (e: Exception) {
			GreencloudCallResult(ok = false, message = e.message ?: "request failed", instances = emptyList())
		} finally {
			connection.disconnect()
		}
	}

	companion object {
		private const val TAG = "GreencloudClient"
		private const val API_ROOT = "https://cp.green.cloud/api"
		private const val TIMEOUT_MS = 20_000
	}
}
