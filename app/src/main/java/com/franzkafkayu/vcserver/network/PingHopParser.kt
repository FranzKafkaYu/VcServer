package com.franzkafkayu.vcserver.network

import java.util.Locale
import java.util.regex.Pattern

private val FROM_HOST = Pattern.compile(
	"(?i)from\\s+([0-9a-f:.]+)"
)
private val TIME_MS = Pattern.compile(
	"(?i)(?:time|时间)[=<]\\s*([0-9.]+)"
)

fun parsePingHop(output: String, targetAddress: String): HopProbeResult {
	val text = output.trim()
	if (text.isEmpty()) {
		return HopProbeResult(hopAddress = null, rttMs = null, reachedTarget = false)
	}
	val time = TIME_MS.matcher(text).let { matcher ->
		if (matcher.find()) matcher.group(1)?.toDoubleOrNull()?.toLong() else null
	}
	val from = FROM_HOST.matcher(text).let { matcher ->
		if (matcher.find()) matcher.group(1)?.trimEnd(':') else null
	}
	val reached = from != null && from.equals(targetAddress, ignoreCase = true) &&
		!text.lowercase(Locale.US).contains("time to live exceeded") &&
		!text.lowercase(Locale.US).contains("ttl exceeded")
	if (from == null) {
		return HopProbeResult(hopAddress = null, rttMs = time, reachedTarget = false)
	}
	return HopProbeResult(
		hopAddress = from,
		rttMs = time,
		reachedTarget = reached
	)
}
