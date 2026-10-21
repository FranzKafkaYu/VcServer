package com.franzkafkayu.vcserver.network

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlin.math.roundToLong
import kotlin.math.sqrt

sealed class PingSample {
	data class Ok(val rttMs: Long) : PingSample()
	data object Failed : PingSample()
}

data class CheckHostNode(
	val id: String,
	val place: String,
	val line: String,
	val pending: Boolean,
	val resolveFailed: Boolean,
	val samples: List<PingSample>
)

data class CheckHostRequest(
	val requestId: String,
	val nodes: List<CheckHostNode>
)

data class PingRowStats(
	val finished: Int,
	val failed: Int,
	val lossRatio: Double?,
	val jitterMs: Double?
)

fun parseCheckHostRequest(json: String): CheckHostRequest {
	val parsed = JsonParser.parseString(json)
	if (!parsed.isJsonObject) {
		throw IllegalArgumentException("check-host start is not an object")
	}
	val obj = parsed.asJsonObject
	if (!acceptedFlag(obj.get("ok"))) {
		throw IllegalArgumentException("check-host rejected the request")
	}
	val requestId = primitiveText(obj.get("request_id")).takeIf { it.isNotBlank() }
		?: throw IllegalArgumentException("missing request id")
	val nodes = obj.getAsJsonObject("nodes")
	val nodeList = nodes?.entrySet()?.map { (id, value) ->
		val fields = if (value.isJsonArray) value.asJsonArray else null
		CheckHostNode(
			id = id,
			place = placeOf(fields),
			line = textAt(fields, 4),
			pending = true,
			resolveFailed = false,
			samples = emptyList()
		)
	}.orEmpty()
	return CheckHostRequest(requestId = requestId, nodes = nodeList)
}

fun applyCheckHostResult(nodes: List<CheckHostNode>, json: String): List<CheckHostNode> {
	val parsed = JsonParser.parseString(json)
	if (parsed.isJsonNull || !parsed.isJsonObject) {
		return nodes
	}
	val obj = parsed.asJsonObject
	return nodes.map { node ->
		if (!obj.has(node.id)) return@map node
		when (val probes = readProbes(obj.get(node.id))) {
			ProbeRead.Pending -> node.copy(pending = true, resolveFailed = false, samples = emptyList())
			ProbeRead.ResolveFailed -> node.copy(pending = false, resolveFailed = true, samples = emptyList())
			is ProbeRead.Ready -> node.copy(pending = false, resolveFailed = false, samples = probes.samples)
		}
	}
}

fun pingRowStats(pending: Boolean, resolveFailed: Boolean, samples: List<PingSample>): PingRowStats {
	if (pending || resolveFailed) {
		return PingRowStats(finished = 0, failed = 0, lossRatio = null, jitterMs = null)
	}
	val failed = samples.count { it is PingSample.Failed }
	val successes = samples.mapNotNull { (it as? PingSample.Ok)?.rttMs }
	val loss = if (samples.isEmpty()) null else failed.toDouble() / samples.size
	return PingRowStats(
		finished = samples.size,
		failed = failed,
		lossRatio = loss,
		jitterMs = populationStdDev(successes)
	)
}

private fun populationStdDev(values: List<Long>): Double? {
	if (values.size < 2) return null
	val mean = values.sum().toDouble() / values.size
	val variance = values.sumOf { value ->
		val delta = value - mean
		delta * delta
	} / values.size
	return sqrt(variance)
}

private sealed class ProbeRead {
	data object Pending : ProbeRead()
	data object ResolveFailed : ProbeRead()
	data class Ready(val samples: List<PingSample>) : ProbeRead()
}

private fun readProbes(value: JsonElement): ProbeRead {
	if (value.isJsonNull) return ProbeRead.Pending
	if (!value.isJsonArray) return ProbeRead.Pending
	val outer = value.asJsonArray
	if (outer.size() == 0) return ProbeRead.Pending
	if (outer.size() == 1 && outer[0].isJsonNull) return ProbeRead.ResolveFailed
	val probes = when {
		isProbe(outer[0]) -> outer
		outer[0].isJsonArray -> {
			val inner = outer[0].asJsonArray
			if (inner.size() == 0) return ProbeRead.Pending
			if (inner.size() == 1 && inner[0].isJsonNull) return ProbeRead.ResolveFailed
			inner
		}
		else -> return ProbeRead.ResolveFailed
	}
	return ProbeRead.Ready(probes.map { probe ->
		if (!probe.isJsonArray) PingSample.Failed else sampleOf(probe.asJsonArray)
	})
}

private fun isProbe(value: JsonElement): Boolean {
	if (!value.isJsonArray) return false
	val first = value.asJsonArray.firstOrNull() ?: return false
	val status = primitiveText(first)
	return status.equals("OK", ignoreCase = true) ||
		status.equals("TIMEOUT", ignoreCase = true) ||
		status.equals("MALFORMED", ignoreCase = true)
}

private fun sampleOf(probe: com.google.gson.JsonArray): PingSample {
	val status = primitiveText(probe.firstOrNull())
	if (!status.equals("OK", ignoreCase = true)) return PingSample.Failed
	val seconds = asSeconds(probe.getOrNull(1)) ?: return PingSample.Failed
	return PingSample.Ok((seconds * 1000.0).roundToLong())
}

private fun acceptedFlag(value: JsonElement?): Boolean {
	if (value == null || !value.isJsonPrimitive) return false
	val primitive = value.asJsonPrimitive
	return when {
		primitive.isBoolean -> primitive.asBoolean
		primitive.isNumber -> primitive.asInt == 1
		else -> primitive.asString == "1"
	}
}

private fun primitiveText(value: JsonElement?): String {
	if (value == null || !value.isJsonPrimitive) return ""
	val primitive = value.asJsonPrimitive
	return when {
		primitive.isString -> primitive.asString
		primitive.isNumber -> primitive.asNumber.toString()
		primitive.isBoolean -> primitive.asBoolean.toString()
		else -> ""
	}
}

private fun asSeconds(value: JsonElement?): Double? {
	if (value == null || !value.isJsonPrimitive) return null
	val primitive = value.asJsonPrimitive
	return when {
		primitive.isNumber -> primitive.asDouble
		primitive.isString -> primitive.asString.toDoubleOrNull()
		else -> null
	}
}

private fun placeOf(fields: com.google.gson.JsonArray?): String {
	val country = textAt(fields, 1)
	val city = textAt(fields, 2)
	return when {
		country.isBlank() -> city
		city.isBlank() -> country
		else -> "$country, $city"
	}
}

private fun textAt(fields: com.google.gson.JsonArray?, index: Int): String {
	val element = fields?.getOrNull(index) ?: return ""
	if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return ""
	return element.asString
}

private fun com.google.gson.JsonArray.getOrNull(index: Int): JsonElement? {
	return if (index in 0 until size()) get(index) else null
}
