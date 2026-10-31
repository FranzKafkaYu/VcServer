package com.franzkafkayu.vcserver.mosh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class MoshSessionContractTest {
	@Test
	fun writeResizeAndCloseReleaseSession() {
		val udp = RecordingUdp()
		val session = MoshSession(udp, MoshCrypto("AAAAAAAAAAAAAAAAAAAAAA"), 80, 24)
		assertEquals(1, MoshSession.liveCount())
		session.write("ls".toByteArray())
		session.resize(100, 30)
		assertTrue(udp.sent.isNotEmpty())
		session.close()
		assertEquals(0, MoshSession.liveCount())
	}

	@Test
	fun closeStopsRemote() {
		val udp = RecordingUdp()
		var stopped = 0
		val session = MoshSession(
			udp,
			MoshCrypto("AAAAAAAAAAAAAAAAAAAAAA"),
			80,
			24,
			stopRemote = { stopped++ }
		)
		session.close()
		assertEquals(1, stopped)
		session.close()
		assertEquals(1, stopped)
	}

	@Test
	fun jumpAndProxyRejected() {
		val jump = com.franzkafkayu.vcserver.models.Server(
			name = "s",
			host = "h",
			username = "root",
			authType = com.franzkafkayu.vcserver.models.AuthType.PASSWORD,
			useMosh = true,
			jumpHostId = 2
		)
		try {
			MoshBootstrap.requireDirect(jump)
			throw AssertionError("expected jump rejection")
		} catch (e: MoshException) {
			assertEquals("MOSH_JUMP_UNSUPPORTED", e.code)
		}
		val proxy = jump.copy(jumpHostId = null, proxyEnabled = true)
		try {
			MoshBootstrap.requireDirect(proxy)
			throw AssertionError("expected proxy rejection")
		} catch (e: MoshException) {
			assertEquals("MOSH_PROXY_UNSUPPORTED", e.code)
		}
	}

	private class RecordingUdp : UdpLink {
		val sent = CopyOnWriteArrayList<ByteArray>()
		override fun send(packet: ByteArray) {
			sent += packet
		}

		override fun receive(timeoutMs: Int): ByteArray? {
			Thread.sleep(timeoutMs.toLong().coerceAtMost(15))
			return null
		}

		override fun close() {}
	}
}
