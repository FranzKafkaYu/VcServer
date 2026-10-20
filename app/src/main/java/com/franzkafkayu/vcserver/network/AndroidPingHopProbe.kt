package com.franzkafkayu.vcserver.network

import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

fun isPublicUnicast(address: InetAddress): Boolean {
	return !address.isAnyLocalAddress &&
		!address.isLoopbackAddress &&
		!address.isLinkLocalAddress &&
		!address.isSiteLocalAddress &&
		!address.isMulticastAddress
}

fun isBogusDirectHit(ttl: Int, result: HopProbeResult, dest: String, destAddress: InetAddress): Boolean {
	if (ttl > 2) return false
	if (!result.reachedTarget) return false
	if (!dest.equals(result.hopAddress, ignoreCase = true)) return false
	return isPublicUnicast(destAddress)
}

class AndroidTtlHopProbe {
	private var sequence = 0
	private var icmpFd: FileDescriptor? = null
	private var icmpOpenFailed = false
	private var pingTtlArgs: ((Int) -> List<String>)? = null
	private var pingTtlChecked = false
	private var pingBin: String = "ping"
	private var pingLeadFlags: List<String>? = null
	private var useIcmp = true

	fun canTraceHops(): Boolean = ensureIcmpFd() != null || pingTtlArgs != null

	fun close() {
		val fd = icmpFd ?: return
		icmpFd = null
		try {
			Os.close(fd)
		} catch (_: Exception) {
		}
	}

	fun tcpConnectMs(target: InetAddress, port: Int, timeoutMs: Int = 5000): Long? {
		val start = System.nanoTime()
		return try {
			Socket().use { socket ->
				socket.connect(InetSocketAddress(target, port), timeoutMs)
			}
			(System.nanoTime() - start) / 1_000_000L
		} catch (_: Exception) {
			null
		}
	}

	fun echoMs(target: InetAddress): Long? {
		val dest = target.hostAddress ?: return null
		return pingOnce(dest, emptyList())?.rttMs
	}

	suspend fun probe(target: InetAddress, ttl: Int): HopProbeResult = withContext(Dispatchers.IO) {
		val dest = target.hostAddress ?: return@withContext HopProbeResult(null, null, false)
		ensurePingTtl(dest, target)
		if (pingTtlArgs != null) {
			val ping = pingOnce(dest, pingTtlArgs!!(ttl)) ?: HopProbeResult(null, null, false)
			if (!isBogusDirectHit(ttl, ping, dest, target)) {
				return@withContext ping
			}
		}
		if (useIcmp) {
			val icmp = icmpProbe(target, dest, ttl)
			if (isUsableHop(icmp) && !isBogusDirectHit(ttl, icmp, dest, target)) {
				return@withContext icmp
			}
		}
		HopProbeResult(null, null, false)
	}

	private fun icmpProbe(target: InetAddress, dest: String, ttl: Int): HopProbeResult {
		if (target !is Inet4Address) return HopProbeResult(null, null, false)
		val fd = ensureIcmpFd() ?: return HopProbeResult(null, null, false)
		val seq = ++sequence
		return try {
			setTtl(fd, ttl)
			val echo = buildKernelIcmpEchoV4(seq)
			val start = System.nanoTime()
			Os.sendto(fd, echo, 0, echo.size, 0, InetSocketAddress(target, 0))
			val buf = ByteArray(512)
			val from = InetSocketAddress(0)
			Os.recvfrom(fd, buf, 0, buf.size, 0, from)
			val rtt = (System.nanoTime() - start) / 1_000_000L
			val hopIp = from.address?.hostAddress
			when (classifyIcmpV4(buf)) {
				IcmpKind.EchoReply -> HopProbeResult(dest, rtt, true)
				IcmpKind.TimeExceeded -> HopProbeResult(usableAddress(hopIp), rtt, false)
				IcmpKind.Other -> HopProbeResult(usableAddress(hopIp), rtt, hopIp.equals(dest, true))
			}
		} catch (_: Exception) {
			HopProbeResult(null, null, false)
		}
	}

	private fun ensurePingTtl(dest: String, destAddress: InetAddress) {
		if (pingTtlChecked) return
		pingTtlChecked = true
		val styles = listOf<(Int) -> List<String>>(
			{ ttl -> listOf("-t", ttl.toString()) },
			{ ttl -> listOf("-T", ttl.toString()) },
			{ ttl -> listOf("-m", ttl.toString()) },
			{ ttl -> listOf("--ttl=$ttl") }
		)
		for (style in styles) {
			val atOne = pingOnce(dest, style(1)) ?: continue
			if (isBogusDirectHit(1, atOne, dest, destAddress)) continue
			if (atOne.hopAddress != null && !atOne.reachedTarget) {
				pingTtlArgs = style
				return
			}
			val atMax = pingOnce(dest, style(64))
			if (atMax?.reachedTarget == true && !atOne.reachedTarget) {
				pingTtlArgs = style
				return
			}
		}
	}

	private fun pingOnce(dest: String, extra: List<String>): HopProbeResult? {
		val known = pingLeadFlags
		if (known != null && extra.isNotEmpty()) {
			return finishPing(pingBin, known + extra, dest)
		}
		val flagSets = listOf(
			listOf("-c", "1", "-W", "1") + extra,
			listOf("-c", "1") + extra
		)
		val bins = listOf(pingBin, "ping", "/system/bin/ping").distinct()
		for (bin in bins) {
			for (flags in flagSets) {
				val parsed = finishPing(bin, flags, dest) ?: continue
				if (parsed.rttMs != null || parsed.hopAddress != null || parsed.reachedTarget) {
					pingBin = bin
					pingLeadFlags = flags.dropLast(extra.size)
					return parsed
				}
			}
		}
		return null
	}

	private fun finishPing(bin: String, flags: List<String>, dest: String): HopProbeResult? {
		val ran = runPing(bin, flags, dest, 1500) ?: return null
		val parsed = parsePingHop(ran.first, dest)
		if (ran.first.isBlank()) return HopProbeResult(null, null, false)
		if (parsed.rttMs == null && parsed.hopAddress == null && !parsed.reachedTarget) {
			return HopProbeResult(null, null, false)
		}
		return parsed.copy(rttMs = parsed.rttMs ?: ran.second)
	}

	private fun runPing(bin: String, flags: List<String>, dest: String, limitMs: Long): Pair<String, Long>? {
		return try {
			val start = System.nanoTime()
			val process = ProcessBuilder(listOf(bin) + flags + dest)
				.redirectErrorStream(true)
				.start()
			val output = StringBuilder()
			val reader = Thread {
				try {
					process.inputStream.bufferedReader().use { source ->
						val buf = CharArray(512)
						while (true) {
							val count = source.read(buf)
							if (count < 0) break
							output.append(buf, 0, count)
						}
					}
				} catch (_: Exception) {
				}
			}
			reader.isDaemon = true
			reader.start()
			if (!process.waitFor(limitMs, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly()
			}
			reader.join(200)
			output.toString() to ((System.nanoTime() - start) / 1_000_000L)
		} catch (_: Exception) {
			null
		}
	}

	private fun ensureIcmpFd(): FileDescriptor? {
		if (icmpOpenFailed) return null
		icmpFd?.let { return it }
		return try {
			val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
			Os.bind(fd, InetSocketAddress(0))
			Os.setsockoptTimeval(
				fd,
				OsConstants.SOL_SOCKET,
				OsConstants.SO_RCVTIMEO,
				StructTimeval.fromMillis(800)
			)
			icmpFd = fd
			fd
		} catch (_: Exception) {
			icmpOpenFailed = true
			null
		}
	}

	private fun isUsableHop(result: HopProbeResult): Boolean {
		return result.reachedTarget || usableAddress(result.hopAddress) != null
	}

	private fun usableAddress(address: String?): String? {
		if (address.isNullOrBlank()) return null
		if (address == "0.0.0.0" || address == "::" || address == "::0") return null
		return address
	}

	private fun setTtl(fd: FileDescriptor, ttl: Int) {
		val value = ttl.coerceIn(1, 255)
		try {
			Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, value)
		} catch (_: Throwable) {
			Os.setsockoptInt(fd, 0, 2, value)
		}
	}
}
