package com.franzkafkayu.vcserver.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress

class PhoneHostTracerouteTest {
	private val v4a = InetAddress.getByName("1.1.1.1")
	private val v4b = InetAddress.getByName("8.8.8.8")
	private val v6 = InetAddress.getByName("2001:db8::1")

	@Test
	fun prefersIpv4WhenBothPresent() {
		assertEquals(v4a, selectTraceAddress(listOf(v6, v4a, v4b)))
	}

	@Test
	fun usesIpv6WhenNoIpv4() {
		assertEquals(v6, selectTraceAddress(listOf(v6)))
	}

	@Test
	fun emptyAddressesAreUnresolved() {
		assertEquals(null, selectTraceAddress(emptyList()))
	}

	@Test
	fun timeoutHopsAreKept() = runBlocking {
		val hops = mutableListOf<TraceHop>()
		val engine = PhoneHostTraceroute(
			resolver = { listOf(v4a) },
			probeHop = { _, ttl ->
				if (ttl < 3) HopProbeResult(null, null, false)
				else HopProbeResult("1.1.1.1", 12, true)
			},
			maxTtl = 5
		)
		engine.trace("example.com") { hops.add(it) }
		assertEquals(3, hops.size)
		assertTrue(hops[0].timedOut)
		assertTrue(hops[1].timedOut)
		assertFalse(hops[2].timedOut)
		assertTrue(hops[2].reachedTarget)
	}

	@Test
	fun unresolvedHostThrows() = runBlocking {
		val engine = PhoneHostTraceroute(
			resolver = { emptyList() },
			probeHop = { _, _ -> error("should not probe") }
		)
		try {
			engine.trace("no.such.host") {}
			fail("expected UnresolvedHostException")
		} catch (_: UnresolvedHostException) {
		}
	}

	@Test
	fun cancelStopsAppendingHops() = runBlocking {
		val hops = mutableListOf<TraceHop>()
		val started = kotlinx.coroutines.CompletableDeferred<Unit>()
		val engine = PhoneHostTraceroute(
			resolver = { listOf(v4a) },
			probeHop = { _, _ ->
				started.complete(Unit)
				kotlinx.coroutines.delay(Long.MAX_VALUE)
				HopProbeResult(null, null, false)
			},
			maxTtl = 30
		)
		val job = launch {
			try {
				engine.trace("example.com") { hops.add(it) }
			} catch (_: CancellationException) {
			}
		}
		started.await()
		job.cancelAndJoin()
		assertEquals(0, hops.size)
	}

	@Test
	fun parseTimeoutKeepsNullAddress() {
		val parsed = parsePingHop("", "1.1.1.1")
		assertEquals(null, parsed.hopAddress)
		assertFalse(parsed.reachedTarget)
	}

	@Test
	fun parseTtlExceededUsesFromAddress() {
		val parsed = parsePingHop(
			"From 192.168.1.1: icmp_seq=1 Time to live exceeded",
			"1.1.1.1"
		)
		assertEquals("192.168.1.1", parsed.hopAddress)
		assertFalse(parsed.reachedTarget)
	}

	@Test
	fun publicDestAtTtl1IsBogusDirectHit() {
		val dest = InetAddress.getByName("104.194.68.113")
		assertTrue(
			isBogusDirectHit(
				ttl = 1,
				result = HopProbeResult("104.194.68.113", 5, true),
				dest = "104.194.68.113",
				destAddress = dest
			)
		)
	}

	@Test
	fun lanGatewayAtTtl1IsNotBogus() {
		val dest = InetAddress.getByName("192.168.1.1")
		assertFalse(
			isBogusDirectHit(
				ttl = 1,
				result = HopProbeResult("192.168.1.1", 2, true),
				dest = "192.168.1.1",
				destAddress = dest
			)
		)
	}

	@Test
	fun icmpEchoChecksumIsValid() {
		val packet = buildIcmpEchoV4(1, 2)
		assertEquals(8, packet[0].toInt())
		assertEquals(0, internetChecksum(packet))
	}

	@Test
	fun kernelIcmpEchoLeavesChecksumToKernel() {
		val packet = buildKernelIcmpEchoV4(7)
		assertEquals(8, packet.size)
		assertEquals(8, packet[0].toInt())
		assertEquals(0, packet[2].toInt())
		assertEquals(0, packet[3].toInt())
	}

	@Test
	fun parseChinesePingTime() {
		val parsed = parsePingHop("来自 1.1.1.1 的回复: 字节=32 时间=12ms TTL=57", "1.1.1.1")
		assertEquals(12L, parsed.rttMs)
	}

	@Test
	fun icmpSkipsIpv4Header() {
		val inner = buildIcmpEchoV4(1, 1)
		inner[0] = 0
		val ip = ByteArray(20 + inner.size)
		ip[0] = 0x45
		inner.copyInto(ip, 20)
		assertEquals(IcmpKind.EchoReply, classifyIcmpV4(ip))
	}

	@Test
	fun parseReplyFromTargetMarksReached() {
		val parsed = parsePingHop(
			"64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=14.2 ms",
			"1.1.1.1"
		)
		assertEquals("1.1.1.1", parsed.hopAddress)
		assertEquals(14L, parsed.rttMs)
		assertTrue(parsed.reachedTarget)
	}
}
