package com.franzkafkayu.vcserver.network

import kotlinx.coroutines.ensureActive
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.coroutineContext

data class TraceHop(
	val ttl: Int,
	val address: String?,
	val rttMs: Long?,
	val timedOut: Boolean,
	val reachedTarget: Boolean
)

data class HopProbeResult(
	val hopAddress: String?,
	val rttMs: Long?,
	val reachedTarget: Boolean
)

fun selectTraceAddress(addresses: List<InetAddress>): InetAddress? {
	if (addresses.isEmpty()) return null
	return addresses.firstOrNull { it is Inet4Address } ?: addresses.first()
}

class PhoneHostTraceroute(
	private val resolver: suspend (String) -> List<InetAddress>,
	private val probeHop: suspend (target: InetAddress, ttl: Int) -> HopProbeResult,
	private val maxTtl: Int = 30
) {
	suspend fun trace(host: String, onHop: (TraceHop) -> Unit): InetAddress {
		val resolved = resolver(host.trim())
		val target = selectTraceAddress(resolved)
			?: throw UnresolvedHostException(host)
		for (ttl in 1..maxTtl) {
			coroutineContext.ensureActive()
			val probe = probeHop(target, ttl)
			val timedOut = probe.hopAddress == null && !probe.reachedTarget
			onHop(
				TraceHop(
					ttl = ttl,
					address = probe.hopAddress,
					rttMs = probe.rttMs,
					timedOut = timedOut,
					reachedTarget = probe.reachedTarget
				)
			)
			if (probe.reachedTarget) break
		}
		return target
	}
}

class UnresolvedHostException(val host: String) : Exception("unresolved:$host")
