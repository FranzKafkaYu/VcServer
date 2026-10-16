package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.LoginSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSessionParseTest {
	@Test
	fun remoteIpv4AndIpv6() {
		val sessions = parseWhoOutput(
			"""
			alice    pts/0        2024-01-15 12:00 (1.2.3.4)
			bob      pts/1        2024-01-15 12:01 (2001:db8::1)
			""".trimIndent()
		)
		assertEquals(2, sessions.size)
		assertEquals(LoginSession("alice", "1.2.3.4"), sessions[0])
		assertEquals(LoginSession("bob", "2001:db8::1"), sessions[1])
	}

	@Test
	fun consoleWithoutHostIsLocal() {
		val sessions = parseWhoOutput("root     tty1         2024-01-15 08:00")
		assertEquals(1, sessions.size)
		assertEquals("root", sessions[0].user)
		assertEquals(LoginSession.SOURCE_LOCAL, sessions[0].source)
	}

	@Test
	fun emptyOutputIsEmptyList() {
		assertEquals(emptyList<LoginSession>(), parseWhoOutput(""))
		assertEquals(emptyList<LoginSession>(), parseWhoOutput("\n  \n"))
	}

	@Test
	fun snapshotCommandIncludesWhoAndOsOnlyDoesNot() {
		assertTrue(ServerMonitoringServiceImpl.CORE_SNAPSHOT_COMMAND.contains("who"))
		assertTrue(ServerMonitoringServiceImpl.CORE_SNAPSHOT_COMMAND.contains("---VC_WHO---"))
		assertFalse(ServerMonitoringServiceImpl.OS_ONLY_COMMAND.contains("who"))
	}
}
