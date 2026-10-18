package com.franzkafkayu.vcserver.services

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class BandwagonServiceInfo(
	val hostname: String?,
	val nodeLocation: String?,
	val plan: String?,
	val monthlyDataBytes: Long?,
	val dataCounter: Long?,
	val dataNextReset: Long?,
	val planDisk: Long?,
	val planRam: Long?,
	val os: String?,
	val ipAddresses: List<String>
)

data class BandwagonMigrateLocation(
	val id: String,
	val description: String?,
	val dataTransferMultiplier: Double?
)

data class BandwagonMigrateLocations(
	val currentLocation: String?,
	val options: List<BandwagonMigrateLocation>
)

data class BandwagonMigrateStart(
	val notificationEmail: String?,
	val newIps: List<String>
)

data class BandwagonCallResult(
	val error: Int,
	val message: String?,
	val veStatus: String?,
	val info: BandwagonServiceInfo? = null,
	val migrateLocations: BandwagonMigrateLocations? = null,
	val migrateStart: BandwagonMigrateStart? = null
)

internal fun parseBandwagonResponse(json: String): BandwagonCallResult {
	val obj = JsonParser.parseString(json).asJsonObject
	val errorElement = obj.get("error")
	val error = if (errorElement != null && !errorElement.isJsonNull) errorElement.asInt else -1
	val messageElement = obj.get("message")
	val message = if (messageElement != null && !messageElement.isJsonNull) messageElement.asString else null
	val statusElement = obj.get("ve_status")
	val veStatus = if (statusElement != null && !statusElement.isJsonNull) statusElement.asString else null
	val info = if (error == 0) parseBandwagonServiceInfo(obj) else null
	val migrateLocations = if (error == 0) parseBandwagonMigrateLocations(obj) else null
	val migrateStart = if (error == 0) parseBandwagonMigrateStart(obj) else null
	return BandwagonCallResult(
		error = error,
		message = message,
		veStatus = veStatus,
		info = info,
		migrateLocations = migrateLocations,
		migrateStart = migrateStart
	)
}

internal fun parseBandwagonServiceInfo(obj: com.google.gson.JsonObject): BandwagonServiceInfo {
	return BandwagonServiceInfo(
		hostname = jsonString(obj, "hostname"),
		nodeLocation = jsonString(obj, "node_location"),
		plan = jsonString(obj, "plan"),
		monthlyDataBytes = jsonLong(obj, "plan_monthly_data"),
		dataCounter = jsonLong(obj, "data_counter"),
		dataNextReset = jsonLong(obj, "data_next_reset"),
		planDisk = jsonLong(obj, "plan_disk"),
		planRam = jsonLong(obj, "plan_ram"),
		os = jsonString(obj, "os"),
		ipAddresses = jsonStringList(obj, "ip_addresses")
	)
}

internal fun parseBandwagonMigrateLocations(obj: com.google.gson.JsonObject): BandwagonMigrateLocations? {
	val current = jsonString(obj, "currentLocation")
	val ids = jsonStringList(obj, "locations")
	if (current == null && ids.isEmpty()) return null
	val descriptions = jsonStringMap(obj, "descriptions")
	val multipliers = jsonDoubleMap(obj, "dataTransferMultipliers")
	val options = ids.map { id ->
		BandwagonMigrateLocation(
			id = id,
			description = descriptions[id],
			dataTransferMultiplier = multipliers[id]
		)
	}
	return BandwagonMigrateLocations(currentLocation = current, options = options)
}

internal fun parseBandwagonMigrateStart(obj: com.google.gson.JsonObject): BandwagonMigrateStart? {
	val email = jsonString(obj, "notificationEmail")
	val ips = jsonStringList(obj, "newIps")
	if (email == null && ips.isEmpty()) return null
	return BandwagonMigrateStart(notificationEmail = email, newIps = ips)
}

internal fun formatBandwagonBytes(bytes: Long): String {
	val gb = 1024.0 * 1024.0 * 1024.0
	val mb = 1024.0 * 1024.0
	return if (bytes >= gb) {
		val value = kotlin.math.round(bytes / gb * 100.0) / 100.0
		if (value % 1.0 == 0.0) "${value.toInt()} GB" else "%.2f GB".format(java.util.Locale.US, value)
	} else {
		val value = kotlin.math.round(bytes / mb * 100.0) / 100.0
		if (value % 1.0 == 0.0) "${value.toInt()} MB" else "%.2f MB".format(java.util.Locale.US, value)
	}
}

private fun jsonString(obj: com.google.gson.JsonObject, name: String): String? {
	val element = obj.get(name) ?: return null
	if (!element.isJsonPrimitive) return null
	return element.asString.takeIf { it.isNotBlank() }
}

private fun jsonLong(obj: com.google.gson.JsonObject, name: String): Long? {
	val element = obj.get(name) ?: return null
	if (!element.isJsonPrimitive) return null
	return try {
		element.asLong
	} catch (_: Exception) {
		element.asString.toLongOrNull()
	}
}

private fun jsonStringList(obj: com.google.gson.JsonObject, name: String): List<String> {
	val element = obj.get(name) ?: return emptyList()
	if (!element.isJsonArray) return emptyList()
	return element.asJsonArray.mapNotNull { item ->
		if (item.isJsonPrimitive) item.asString.takeIf { it.isNotBlank() } else null
	}
}

private fun jsonStringMap(obj: com.google.gson.JsonObject, name: String): Map<String, String> {
	val element = obj.get(name) ?: return emptyMap()
	if (!element.isJsonObject) return emptyMap()
	return element.asJsonObject.entrySet().mapNotNull { entry ->
		val value = entry.value
		if (value.isJsonPrimitive) value.asString.takeIf { it.isNotBlank() }?.let { entry.key to it } else null
	}.toMap()
}

private fun jsonDoubleMap(obj: com.google.gson.JsonObject, name: String): Map<String, Double> {
	val element = obj.get(name) ?: return emptyMap()
	if (!element.isJsonObject) return emptyMap()
	return element.asJsonObject.entrySet().mapNotNull { entry ->
		val value = entry.value
		if (!value.isJsonPrimitive) return@mapNotNull null
		val number = try {
			value.asDouble
		} catch (_: Exception) {
			value.asString.toDoubleOrNull()
		} ?: return@mapNotNull null
		entry.key to number
	}.toMap()
}

interface BandwagonClient {
	suspend fun call(
		action: String,
		veid: String,
		apiKey: String,
		timeoutMs: Int,
		extraFields: Map<String, String> = emptyMap()
	): BandwagonCallResult
}

class HttpBandwagonClient : BandwagonClient {
	override suspend fun call(
		action: String,
		veid: String,
		apiKey: String,
		timeoutMs: Int,
		extraFields: Map<String, String>
	): BandwagonCallResult = withContext(Dispatchers.IO) {
		val connection = (URL("$API_ROOT$action").openConnection() as HttpURLConnection).apply {
			requestMethod = "POST"
			doOutput = true
			connectTimeout = timeoutMs
			readTimeout = timeoutMs
			setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		}
		try {
			val payload = extraFields.filterKeys { it != "veid" && it != "api_key" } +
				mapOf("veid" to veid, "api_key" to apiKey)
			val body = formBody(payload)
			connection.outputStream.use { stream ->
				stream.write(body.toByteArray(StandardCharsets.UTF_8))
			}
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
			Log.i(TAG, "action=$action veid=$veid http=$code")
			if (text.isBlank()) {
				BandwagonCallResult(error = -1, message = "empty response", veStatus = null)
			} else {
				parseBandwagonResponse(text)
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun formBody(fields: Map<String, String>): String {
		return fields.entries.joinToString("&") { (key, value) ->
			"${encode(key)}=${encode(value)}"
		}
	}

	private fun encode(value: String): String {
		return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
	}

	companion object {
		private const val TAG = "BandwagonClient"
		private const val API_ROOT = "https://api.64clouds.com/v1/"
		const val TIMEOUT_POWER_MS = 15_000
		const val TIMEOUT_LIVE_MS = 20_000
		const val ACTION_INFO = "getServiceInfo"
		const val ACTION_LIVE = "getLiveServiceInfo"
		const val ACTION_START = "start"
		const val ACTION_STOP = "stop"
		const val ACTION_RESTART = "restart"
		const val ACTION_MIGRATE_LOCATIONS = "migrate/getLocations"
		const val ACTION_MIGRATE_START = "migrate/start"
		const val TIMEOUT_MIGRATE_MS = 30_000
	}
}
