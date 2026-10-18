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

data class CloudconeCallResult(
	val ok: Boolean,
	val message: String?,
	val instances: List<CloudconeInstance>
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
	return CloudconeCallResult(ok = true, message = message, instances = instances)
}

private fun extractInstances(obj: JsonObject): List<CloudconeInstance> {
	arrayField(obj, "instances")?.let { return instancesFrom(it) }
	val data = obj.get("data")
	if (data != null && data.isJsonArray) return instancesFrom(data.asJsonArray)
	val wrapped = obj.get("__data")
	if (wrapped != null && wrapped.isJsonArray) return instancesFrom(wrapped.asJsonArray)
	if (wrapped != null && wrapped.isJsonObject) {
		arrayField(wrapped.asJsonObject, "instances")?.let { return instancesFrom(it) }
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
	val idElement = obj.get("id") ?: return null
	if (!idElement.isJsonPrimitive) return null
	val id = idElement.asString
	if (id.isBlank()) return null
	return CloudconeInstance(
		id = id,
		address = addressOf(obj),
		powerStatus = powerOf(obj)
	)
}

private fun addressOf(obj: JsonObject): String? {
	textField(obj, "ip")?.let { return it }
	textField(obj, "mainip")?.let { return it }
	val ips = obj.get("ips") ?: return null
	if (ips.isJsonPrimitive && ips.asJsonPrimitive.isString) {
		return ips.asString.takeIf { it.isNotBlank() }
	}
	if (ips.isJsonArray && ips.asJsonArray.size() > 0) {
		val first = ips.asJsonArray[0]
		if (first.isJsonPrimitive) return first.asString
	}
	return null
}

private fun powerOf(obj: JsonObject): String? {
	val state = obj.get("state")
	if (state != null && state.isJsonObject) {
		textField(state.asJsonObject, "name")?.let { return it }
	}
	for (name in listOf("power_status", "state", "status")) {
		val element = obj.get(name) ?: continue
		if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
			val value = element.asString
			if (value.isNotBlank()) return value
		}
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
	val element = obj.get(name) ?: return null
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
		return request("GET", "compute/$instanceId/information", apiKey, hash)
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
		val connection = (URL("$API_ROOT$path").openConnection() as HttpURLConnection).apply {
			requestMethod = method
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
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
			Log.i(TAG, "path=$path http=$code")
			val parsed = try {
				if (text.isBlank() && code in 200..299) {
					CloudconeCallResult(ok = true, message = null, instances = emptyList())
				} else {
					parseCloudconeBody(text)
				}
			} catch (e: Exception) {
				CloudconeCallResult(ok = false, message = "http $code", instances = emptyList())
			}
			if (code !in 200..299) {
				parsed.copy(ok = false, message = parsed.message ?: "http $code")
			} else {
				parsed
			}
		} finally {
			connection.disconnect()
		}
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
