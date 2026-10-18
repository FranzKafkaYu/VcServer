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

data class BandwagonUsagePoint(
	val timestampSec: Long,
	val value: Double
)

data class BandwagonUsageStats(
	val cpuUsage: List<BandwagonUsagePoint> = emptyList(),
	val networkInBytes: List<BandwagonUsagePoint> = emptyList(),
	val networkOutBytes: List<BandwagonUsagePoint> = emptyList(),
	val networkInBps: List<BandwagonUsagePoint> = emptyList(),
	val networkOutBps: List<BandwagonUsagePoint> = emptyList()
) {
	fun isEmpty(): Boolean {
		return cpuUsage.isEmpty() &&
			networkInBytes.isEmpty() &&
			networkOutBytes.isEmpty() &&
			networkInBps.isEmpty() &&
			networkOutBps.isEmpty()
	}
}

enum class BandwagonUsageRange {
	DAY,
	WEEK,
	MONTH,
	YEAR,
	MAX
}

data class BandwagonCallResult(
	val error: Int,
	val message: String?,
	val veStatus: String?,
	val info: BandwagonServiceInfo? = null,
	val migrateLocations: BandwagonMigrateLocations? = null,
	val migrateStart: BandwagonMigrateStart? = null,
	val password: String? = null,
	val usage: BandwagonUsageStats? = null
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
	val password = if (error == 0) jsonString(obj, "password") else null
	val usage = if (error == 0) parseBandwagonUsageStats(obj) else null
	return BandwagonCallResult(
		error = error,
		message = message,
		veStatus = veStatus,
		info = info,
		migrateLocations = migrateLocations,
		migrateStart = migrateStart,
		password = password,
		usage = usage
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

internal fun parseBandwagonUsageStats(obj: com.google.gson.JsonObject): BandwagonUsageStats? {
	val data = obj.get("data")
	val parsed = when {
		data == null || data.isJsonNull -> parseUsageObject(obj)
		data.isJsonObject -> parseUsageObject(data.asJsonObject).takeUnless { it.isEmpty() }
			?: parseUsageTimestampMap(data.asJsonObject)
		data.isJsonArray -> parseUsageArray(data.asJsonArray)
		else -> null
	} ?: return null
	val filled = fillDerivedBps(parsed)
	return filled.takeUnless { it.isEmpty() }
}

internal fun sliceBandwagonUsage(
	stats: BandwagonUsageStats,
	range: BandwagonUsageRange,
	nowSec: Long = System.currentTimeMillis() / 1000L
): BandwagonUsageStats {
	val start = when (range) {
		BandwagonUsageRange.DAY -> nowSec - 86_400L
		BandwagonUsageRange.WEEK -> nowSec - 7L * 86_400L
		BandwagonUsageRange.MONTH -> nowSec - 30L * 86_400L
		BandwagonUsageRange.YEAR -> nowSec - 365L * 86_400L
		BandwagonUsageRange.MAX -> Long.MIN_VALUE
	}
	return BandwagonUsageStats(
		cpuUsage = downsampleUsage(stats.cpuUsage.filter { it.timestampSec >= start }),
		networkInBytes = downsampleUsage(stats.networkInBytes.filter { it.timestampSec >= start }),
		networkOutBytes = downsampleUsage(stats.networkOutBytes.filter { it.timestampSec >= start }),
		networkInBps = downsampleUsage(stats.networkInBps.filter { it.timestampSec >= start }),
		networkOutBps = downsampleUsage(stats.networkOutBps.filter { it.timestampSec >= start })
	)
}

private fun parseUsageObject(obj: com.google.gson.JsonObject): BandwagonUsageStats {
	return BandwagonUsageStats(
		cpuUsage = parseUsageSeries(obj, CPU_KEYS),
		networkInBytes = parseUsageSeries(obj, NET_IN_BYTES_KEYS),
		networkOutBytes = parseUsageSeries(obj, NET_OUT_BYTES_KEYS),
		networkInBps = parseUsageSeries(obj, NET_IN_BPS_KEYS),
		networkOutBps = parseUsageSeries(obj, NET_OUT_BPS_KEYS)
	)
}

private fun parseUsageTimestampMap(obj: com.google.gson.JsonObject): BandwagonUsageStats? {
	val cpu = mutableListOf<BandwagonUsagePoint>()
	val inbound = mutableListOf<BandwagonUsagePoint>()
	val outbound = mutableListOf<BandwagonUsagePoint>()
	for (entry in obj.entrySet()) {
		val ts = entry.key.toDoubleOrNull()?.let { normalizeTimestamp(it) } ?: continue
		val value = entry.value
		when {
			value.isJsonObject -> {
				val row = value.asJsonObject
				jsonNumberFrom(row, CPU_KEYS)?.let { cpu += BandwagonUsagePoint(ts, it) }
				jsonNumberFrom(row, NET_IN_BYTES_KEYS)?.let { inbound += BandwagonUsagePoint(ts, it) }
				jsonNumberFrom(row, NET_OUT_BYTES_KEYS)?.let { outbound += BandwagonUsagePoint(ts, it) }
			}
			value.isJsonArray -> {
				val row = value.asJsonArray
				if (row.size() == 0) continue
				jsonNumber(row[0])?.let { cpu += BandwagonUsagePoint(ts, it) }
				if (row.size() > 1) jsonNumber(row[1])?.let { inbound += BandwagonUsagePoint(ts, it) }
				if (row.size() > 2) jsonNumber(row[2])?.let { outbound += BandwagonUsagePoint(ts, it) }
			}
			else -> jsonNumber(value)?.let { cpu += BandwagonUsagePoint(ts, it) }
		}
	}
	if (cpu.isEmpty() && inbound.isEmpty() && outbound.isEmpty()) return null
	cpu.sortBy { it.timestampSec }
	inbound.sortBy { it.timestampSec }
	outbound.sortBy { it.timestampSec }
	return BandwagonUsageStats(cpuUsage = cpu, networkInBytes = inbound, networkOutBytes = outbound)
}

private fun parseUsageArray(rows: com.google.gson.JsonArray): BandwagonUsageStats? {
	if (rows.size() == 0) return BandwagonUsageStats()
	val first = rows[0]
	return if (first.isJsonObject) parseUsageRowObjects(rows) else parseUsageRows(rows)
}

private fun parseUsageRowObjects(rows: com.google.gson.JsonArray): BandwagonUsageStats? {
	val cpu = mutableListOf<BandwagonUsagePoint>()
	val inbound = mutableListOf<BandwagonUsagePoint>()
	val outbound = mutableListOf<BandwagonUsagePoint>()
	val inBps = mutableListOf<BandwagonUsagePoint>()
	val outBps = mutableListOf<BandwagonUsagePoint>()
	for (item in rows) {
		if (!item.isJsonObject) continue
		val row = item.asJsonObject
		val ts = timestampFromObject(row) ?: continue
		jsonNumberFrom(row, CPU_KEYS)?.let { cpu += BandwagonUsagePoint(ts, it) }
		jsonNumberFrom(row, NET_IN_BYTES_KEYS)?.let { inbound += BandwagonUsagePoint(ts, it) }
		jsonNumberFrom(row, NET_OUT_BYTES_KEYS)?.let { outbound += BandwagonUsagePoint(ts, it) }
		jsonNumberFrom(row, NET_IN_BPS_KEYS)?.let { inBps += BandwagonUsagePoint(ts, it) }
		jsonNumberFrom(row, NET_OUT_BPS_KEYS)?.let { outBps += BandwagonUsagePoint(ts, it) }
	}
	if (cpu.isEmpty() && inbound.isEmpty() && outbound.isEmpty() && inBps.isEmpty() && outBps.isEmpty()) {
		return null
	}
	return BandwagonUsageStats(
		cpuUsage = cpu,
		networkInBytes = inbound,
		networkOutBytes = outbound,
		networkInBps = inBps,
		networkOutBps = outBps
	)
}

private fun parseUsageRows(rows: com.google.gson.JsonArray): BandwagonUsageStats? {
	if (rows.size() == 0) return BandwagonUsageStats()
	val cpu = mutableListOf<BandwagonUsagePoint>()
	val inbound = mutableListOf<BandwagonUsagePoint>()
	val outbound = mutableListOf<BandwagonUsagePoint>()
	for (item in rows) {
		if (!item.isJsonArray) continue
		val row = item.asJsonArray
		if (row.size() < 2) continue
		val ts = jsonNumber(row[0])?.let { normalizeTimestamp(it) } ?: continue
		jsonNumber(row[1])?.let { cpu += BandwagonUsagePoint(ts, it) }
		if (row.size() > 2) {
			jsonNumber(row[2])?.let { inbound += BandwagonUsagePoint(ts, it) }
		}
		if (row.size() > 3) {
			jsonNumber(row[3])?.let { outbound += BandwagonUsagePoint(ts, it) }
		}
	}
	if (cpu.isEmpty() && inbound.isEmpty() && outbound.isEmpty()) return null
	return BandwagonUsageStats(cpuUsage = cpu, networkInBytes = inbound, networkOutBytes = outbound)
}

private fun parseUsageSeries(obj: com.google.gson.JsonObject, names: List<String>): List<BandwagonUsagePoint> {
	for (name in names) {
		val element = obj.get(name) ?: continue
		val series = parseUsageSeriesElement(element)
		if (series.isNotEmpty()) return series
	}
	return emptyList()
}

private fun parseUsageSeriesElement(element: com.google.gson.JsonElement): List<BandwagonUsagePoint> {
	return when {
		element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
			when {
				item.isJsonArray && item.asJsonArray.size() >= 2 -> {
					val pair = item.asJsonArray
					val ts = jsonNumber(pair[0])?.let { normalizeTimestamp(it) } ?: return@mapNotNull null
					val value = jsonNumber(pair[1]) ?: return@mapNotNull null
					BandwagonUsagePoint(ts, value)
				}
				item.isJsonObject -> {
					val row = item.asJsonObject
					val ts = timestampFromObject(row) ?: return@mapNotNull null
					val value = jsonNumberFrom(row, "value") ?: jsonNumberFrom(row, "y") ?: return@mapNotNull null
					BandwagonUsagePoint(ts, value)
				}
				else -> null
			}
		}.sortedBy { it.timestampSec }
		element.isJsonObject -> element.asJsonObject.entrySet().mapNotNull { entry ->
			val ts = entry.key.toDoubleOrNull()?.let { normalizeTimestamp(it) } ?: return@mapNotNull null
			val value = jsonNumber(entry.value) ?: return@mapNotNull null
			BandwagonUsagePoint(ts, value)
		}.sortedBy { it.timestampSec }
		else -> emptyList()
	}
}

private fun timestampFromObject(row: com.google.gson.JsonObject): Long? {
	val raw = jsonNumberFrom(row, "timestamp")
		?: jsonNumberFrom(row, "time")
		?: jsonNumberFrom(row, "t")
		?: jsonNumberFrom(row, "x")
		?: return null
	return normalizeTimestamp(raw)
}

private fun jsonNumberFrom(obj: com.google.gson.JsonObject, name: String): Double? {
	val element = obj.get(name) ?: return null
	return jsonNumber(element)
}

private fun jsonNumberFrom(obj: com.google.gson.JsonObject, names: List<String>): Double? {
	for (name in names) {
		jsonNumberFrom(obj, name)?.let { return it }
	}
	return null
}

private val CPU_KEYS = listOf("cpu_usage", "cpu", "cpuAvg", "cpu_avg")
private val NET_IN_BYTES_KEYS = listOf(
	"network_in_bytes", "net_in", "network_in", "networkIn", "bw_in", "vnstat_in"
)
private val NET_OUT_BYTES_KEYS = listOf(
	"network_out_bytes", "net_out", "network_out", "networkOut", "bw_out", "vnstat_out"
)
private val NET_IN_BPS_KEYS = listOf("network_in_bps", "net_in_bps")
private val NET_OUT_BPS_KEYS = listOf("network_out_bps", "net_out_bps")

private fun fillDerivedBps(stats: BandwagonUsageStats): BandwagonUsageStats {
	return stats.copy(
		networkInBps = stats.networkInBps.ifEmpty { deriveBps(stats.networkInBytes) },
		networkOutBps = stats.networkOutBps.ifEmpty { deriveBps(stats.networkOutBytes) }
	)
}

private fun deriveBps(bytes: List<BandwagonUsagePoint>): List<BandwagonUsagePoint> {
	if (bytes.size < 2) return emptyList()
	return bytes.zipWithNext { previous, current ->
		val dt = (current.timestampSec - previous.timestampSec).coerceAtLeast(1L).toDouble()
		val delta = (current.value - previous.value).coerceAtLeast(0.0)
		BandwagonUsagePoint(current.timestampSec, delta * 8.0 / dt)
	}
}

private fun downsampleUsage(points: List<BandwagonUsagePoint>, maxPoints: Int = 96): List<BandwagonUsagePoint> {
	if (points.size <= maxPoints) return points
	val step = points.size.toDouble() / maxPoints
	return (0 until maxPoints).map { index ->
		points[(index * step).toInt().coerceIn(0, points.lastIndex)]
	}
}

private fun normalizeTimestamp(value: Double): Long {
	return if (value > 10_000_000_000.0) (value / 1000.0).toLong() else value.toLong()
}

private fun jsonNumber(element: com.google.gson.JsonElement): Double? {
	if (!element.isJsonPrimitive) return null
	return try {
		element.asDouble
	} catch (_: Exception) {
		element.asString.toDoubleOrNull()
	}
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
		val payload = extraFields.filterKeys { it != "veid" && it != "api_key" } +
			mapOf("veid" to veid, "api_key" to apiKey)
		val query = formBody(payload)
		val useGet = action == ACTION_USAGE
		val requestUrl = if (useGet) "$API_ROOT$action?$query" else "$API_ROOT$action"
		val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
			requestMethod = if (useGet) "GET" else "POST"
			doOutput = !useGet
			connectTimeout = timeoutMs
			readTimeout = timeoutMs
			if (!useGet) {
				setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
			}
		}
		try {
			if (!useGet) {
				val body = query
				connection.outputStream.use { stream ->
					stream.write(body.toByteArray(StandardCharsets.UTF_8))
				}
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
		const val ACTION_RESET_ROOT = "resetRootPassword"
		const val ACTION_USAGE = "getRawUsageStats"
		const val TIMEOUT_MIGRATE_MS = 30_000
		const val TIMEOUT_USAGE_MS = 20_000
	}
}
