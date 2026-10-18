package com.franzkafkayu.vcserver.services

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager

data class VirtualizorVps(
	val id: String,
	val hostname: String?,
	val address: String?,
	val powerStatus: String?
)

data class VirtualizorBandwidth(
	val usedGb: Double?,
	val limitGb: Double?,
	val percent: Double?,
	val inGb: Double?,
	val outGb: Double?,
	val unlimited: Boolean
)

data class VirtualizorCallResult(
	val ok: Boolean,
	val message: String?,
	val instances: List<VirtualizorVps> = emptyList(),
	val certPinSha256: String? = null,
	val bandwidth: VirtualizorBandwidth? = null
)

internal fun parseVirtualizorBody(json: String, powerCall: Boolean): VirtualizorCallResult {
	if (json.isBlank()) {
		return VirtualizorCallResult(ok = false, message = "empty response")
	}
	val element = try {
		JsonParser.parseString(json)
	} catch (_: Exception) {
		return VirtualizorCallResult(ok = false, message = "invalid response")
	}
	if (!element.isJsonObject) {
		return VirtualizorCallResult(ok = false, message = "invalid response")
	}
	val obj = element.asJsonObject
	if (isLoginFailure(obj)) {
		return VirtualizorCallResult(ok = false, message = errorMessage(obj) ?: "login failed")
	}
	if (powerCall) {
		val done = obj.get("done")
		val accepted = done != null && !done.isJsonNull &&
			!(done.isJsonPrimitive && done.asJsonPrimitive.isBoolean && !done.asBoolean)
		if (!accepted && obj.has("error")) {
			return VirtualizorCallResult(ok = false, message = errorMessage(obj) ?: "request failed")
		}
		if (!accepted) {
			return VirtualizorCallResult(ok = false, message = errorMessage(obj) ?: "request failed")
		}
		return VirtualizorCallResult(ok = true, message = errorMessage(obj))
	}
	return VirtualizorCallResult(ok = true, message = null, instances = extractVps(obj))
}

internal fun parseVirtualizorBandwidth(json: String): VirtualizorCallResult {
	if (json.isBlank()) {
		return VirtualizorCallResult(ok = false, message = "empty response")
	}
	val element = try {
		JsonParser.parseString(json)
	} catch (_: Exception) {
		return VirtualizorCallResult(ok = false, message = "invalid response")
	}
	if (!element.isJsonObject) {
		return VirtualizorCallResult(ok = false, message = "invalid response")
	}
	val obj = element.asJsonObject
	if (isLoginFailure(obj)) {
		return VirtualizorCallResult(ok = false, message = errorMessage(obj) ?: "login failed")
	}
	val band = obj.get("bandwidth")
	if (band == null || !band.isJsonObject) {
		return VirtualizorCallResult(ok = false, message = errorMessage(obj) ?: "no bandwidth")
	}
	val bw = band.asJsonObject
	val limitGb = jsonDouble(bw, "limit_gb")
	val limit = jsonDouble(bw, "limit")
	val usedGb = jsonDouble(bw, "used_gb") ?: jsonDouble(bw, "used")?.div(1024.0)
	val unlimited = (limitGb == null || limitGb == 0.0) && (limit == null || limit == 0.0)
	val inGb = sumUsageGb(bw.get("in"))
	val outGb = sumUsageGb(bw.get("out"))
	return VirtualizorCallResult(
		ok = true,
		message = null,
		bandwidth = VirtualizorBandwidth(
			usedGb = usedGb,
			limitGb = limitGb,
			percent = jsonDouble(bw, "percent"),
			inGb = inGb,
			outGb = outGb,
			unlimited = unlimited
		)
	)
}

internal fun formatBandwidthGb(value: Double): String {
	val rounded = kotlin.math.round(value * 100.0) / 100.0
	return if (rounded % 1.0 == 0.0) {
		rounded.toInt().toString()
	} else {
		"%.2f".format(java.util.Locale.US, rounded)
	}
}

internal fun formatBandwidthPercent(value: Double, decimals: Int): String {
	return "%.${decimals}f".format(java.util.Locale.US, value)
}

private fun jsonDouble(obj: JsonObject, name: String): Double? {
	val element = obj.get(name) ?: return null
	if (!element.isJsonPrimitive) return null
	return try {
		element.asDouble
	} catch (_: Exception) {
		element.asString.toDoubleOrNull()
	}
}

private fun sumUsageGb(element: com.google.gson.JsonElement?): Double? {
	if (element == null || element.isJsonNull) return null
	val mb = when {
		element.isJsonPrimitive -> element.asDouble
		element.isJsonObject -> element.asJsonObject.entrySet().sumOf { entry ->
			if (entry.value.isJsonPrimitive) entry.value.asDouble else 0.0
		}
		element.isJsonArray -> element.asJsonArray.sumOf { item ->
			if (item.isJsonPrimitive) item.asDouble else 0.0
		}
		else -> return null
	}
	if (mb == 0.0) return 0.0
	return mb / 1024.0
}

internal fun mapVirtualizorPowerStatus(raw: String?): String? {
	if (raw.isNullOrBlank()) return null
	return when (raw.trim()) {
		"0" -> "stopped"
		"1" -> "running"
		"stopped", "running", "suspended" -> raw.trim()
		else -> null
	}
}

private fun extractVps(obj: JsonObject): List<VirtualizorVps> {
	val vs = obj.get("vs")
	if (vs != null && vs.isJsonObject) {
		return vs.asJsonObject.entrySet().mapNotNull { entry ->
			if (entry.value.isJsonObject) vpsFrom(entry.value.asJsonObject, entry.key) else null
		}
	}
	return obj.entrySet().mapNotNull { entry ->
		if (!entry.key.all { it.isDigit() }) return@mapNotNull null
		if (entry.value.isJsonObject) vpsFrom(entry.value.asJsonObject, entry.key) else null
	}
}

private fun vpsFrom(obj: JsonObject, fallbackId: String): VirtualizorVps? {
	val id = textOrNumber(obj, "vpsid") ?: fallbackId.takeIf { it.isNotBlank() } ?: return null
	return VirtualizorVps(
		id = id,
		hostname = textOrNumber(obj, "hostname"),
		address = firstIp(obj),
		powerStatus = mapVirtualizorPowerStatus(textOrNumber(obj, "status"))
	)
}

private fun firstIp(obj: JsonObject): String? {
	val ips = obj.get("ips") ?: return null
	if (ips.isJsonPrimitive) return ips.asString.takeIf { it.isNotBlank() }
	if (!ips.isJsonObject) return null
	return ips.asJsonObject.entrySet().firstNotNullOfOrNull { entry ->
		if (entry.value.isJsonPrimitive) entry.value.asString.takeIf { it.isNotBlank() } else null
	}
}

private fun isLoginFailure(obj: JsonObject): Boolean {
	val error = errorMessage(obj)?.lowercase().orEmpty()
	if (error.contains("invalid") || error.contains("login") || error.contains("password")) return true
	val title = textOrNumber(obj, "title")?.lowercase().orEmpty()
	return title.contains("invalid") || title.contains("login")
}

private fun errorMessage(obj: JsonObject): String? {
	textOrNumber(obj, "error_msg")?.let { return it }
	val error = obj.get("error")
	if (error != null && error.isJsonPrimitive) {
		val text = error.asString
		if (text.isNotBlank() && text != "0" && text != "false") return text
	}
	val done = obj.get("done")
	if (done != null && done.isJsonObject) {
		textOrNumber(done.asJsonObject, "msg")?.let { return it }
	}
	return textOrNumber(obj, "message")
}

private fun textOrNumber(obj: JsonObject, name: String): String? {
	val element = obj.get(name) ?: return null
	if (!element.isJsonPrimitive) return null
	val value = element.asString
	return value.takeIf { it.isNotBlank() }
}

interface VirtualizorClient {
	suspend fun list(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		certPinSha256: String?
	): VirtualizorCallResult

	suspend fun power(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		action: String,
		certPinSha256: String?
	): VirtualizorCallResult

	suspend fun bandwidth(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		show: String?,
		certPinSha256: String?
	): VirtualizorCallResult
}

class HttpVirtualizorClient : VirtualizorClient {
	override suspend fun list(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		certPinSha256: String?
	): VirtualizorCallResult = call(host, port, apiKey, apiPass, "listvs", null, certPinSha256, ParseKind.LIST)

	override suspend fun power(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		action: String,
		certPinSha256: String?
	): VirtualizorCallResult = call(host, port, apiKey, apiPass, action, vpsid, certPinSha256, ParseKind.POWER)

	override suspend fun bandwidth(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		show: String?,
		certPinSha256: String?
	): VirtualizorCallResult = call(
		host,
		port,
		apiKey,
		apiPass,
		ACTION_BANDWIDTH,
		vpsid,
		certPinSha256,
		ParseKind.BANDWIDTH,
		show
	)

	private suspend fun call(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		act: String,
		vpsid: String?,
		certPinSha256: String?,
		kind: ParseKind,
		show: String? = null
	): VirtualizorCallResult = withContext(Dispatchers.IO) {
		val url = buildUrl(host, port, act, apiKey, apiPass, vpsid, kind, show)
		try {
			request(url, certPinSha256, kind)
		} catch (e: SSLHandshakeException) {
			val pin = capturePin(url) ?: return@withContext VirtualizorCallResult(
				ok = false,
				message = e.message ?: "certificate error"
			)
			VirtualizorCallResult(ok = false, message = "certificate untrusted", certPinSha256 = pin)
		} catch (e: Exception) {
			VirtualizorCallResult(ok = false, message = e.message ?: "request failed")
		}
	}

	private fun request(url: URL, certPinSha256: String?, kind: ParseKind): VirtualizorCallResult {
		val connection = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = TIMEOUT_MS
			readTimeout = TIMEOUT_MS
			instanceFollowRedirects = true
			setRequestProperty("Accept", "application/json")
			if (this is HttpsURLConnection) {
				sslSocketFactory = sslContext(certPinSha256).socketFactory
			}
		}
		try {
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
			Log.i(TAG, "act=${url.query?.substringAfter("act=")?.substringBefore('&')} http=$code")
			val parsed = when (kind) {
				ParseKind.BANDWIDTH -> parseVirtualizorBandwidth(text)
				ParseKind.POWER -> parseVirtualizorBody(text, powerCall = true)
				ParseKind.LIST -> parseVirtualizorBody(text, powerCall = false)
			}
			return if (code in 200..299) parsed else parsed.copy(ok = false, message = parsed.message ?: "http $code")
		} finally {
			connection.disconnect()
		}
	}

	private fun capturePin(url: URL): String? {
		val capturing = CapturingTrustManager()
		val context = SSLContext.getInstance("TLS")
		context.init(null, arrayOf(capturing), null)
		val connection = (url.openConnection() as? HttpsURLConnection) ?: return null
		connection.sslSocketFactory = context.socketFactory
		connection.connectTimeout = TIMEOUT_MS
		connection.readTimeout = TIMEOUT_MS
		return try {
			connection.connect()
			null
		} catch (_: Exception) {
			capturing.pin
		} finally {
			connection.disconnect()
		}
	}

	private fun sslContext(pin: String?): SSLContext {
		val context = SSLContext.getInstance("TLS")
		if (pin.isNullOrBlank()) {
			context.init(null, null, null)
			return context
		}
		val default = systemTrustManager()
		val pinned = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
				default.checkClientTrusted(chain, authType)
			}

			override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
				val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
				if (!sha256Hex(leaf).equals(pin, ignoreCase = true)) {
					throw CertificateException("pin mismatch")
				}
			}

			override fun getAcceptedIssuers(): Array<X509Certificate> = default.acceptedIssuers
		}
		context.init(null, arrayOf(pinned), null)
		return context
	}

	private fun systemTrustManager(): X509TrustManager {
		val factory = javax.net.ssl.TrustManagerFactory.getInstance(
			javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
		)
		factory.init(null as java.security.KeyStore?)
		return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
	}

	private fun buildUrl(
		host: String,
		port: Int,
		act: String,
		apiKey: String,
		apiPass: String,
		vpsid: String?,
		kind: ParseKind,
		show: String?
	): URL {
		val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
		val encodedPass = URLEncoder.encode(apiPass, "UTF-8")
		val extra = buildString {
			if (vpsid != null) {
				append("&svs=").append(URLEncoder.encode(vpsid, "UTF-8"))
			}
			if (kind == ParseKind.POWER) {
				append("&do=1")
			}
			if (!show.isNullOrBlank()) {
				append("&show=").append(URLEncoder.encode(show, "UTF-8"))
			}
		}
		return URL("https://${host.trim()}:$port/index.php?act=$act&api=json&apikey=$encodedKey&apipass=$encodedPass$extra")
	}

	companion object {
		private const val TAG = "VirtualizorClient"
		private const val TIMEOUT_MS = 20_000
		const val ACTION_START = "start"
		const val ACTION_STOP = "stop"
		const val ACTION_RESTART = "restart"
		const val ACTION_BANDWIDTH = "bandwidth"
	}
}

private enum class ParseKind { LIST, POWER, BANDWIDTH }

private class CapturingTrustManager : X509TrustManager {
	var pin: String? = null

	override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

	override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
		pin = chain.firstOrNull()?.let { sha256Hex(it) }
		throw CertificateException("pin required")
	}

	override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun sha256Hex(cert: X509Certificate): String {
	val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
	return digest.joinToString("") { byte -> "%02x".format(byte) }
}
