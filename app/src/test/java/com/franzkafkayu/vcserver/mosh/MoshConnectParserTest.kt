package com.franzkafkayu.vcserver.mosh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoshConnectParserTest {
	@Test
	fun parsesConnectLine() {
		val info = MoshConnectParser.parse(
			"MOSH CONNECT 60001 AAAAAAAAAAAAAAAAAAAAAA\n"
		)
		assertNotNull(info)
		assertEquals(60001, info!!.udpPort)
		assertEquals("AAAAAAAAAAAAAAAAAAAAAA", info.key)
	}

	@Test
	fun parsesConnectLineAmongWarnings() {
		val info = MoshConnectParser.parse(
			"mosh-server (mosh 1.4.0)\r\nMOSH CONNECT 60002 AAAAAAAAAAAAAAAAAAAAAA\r\n"
		)
		assertNotNull(info)
		assertEquals(60002, info!!.udpPort)
	}

	@Test
	fun parsesSpecifiedPortCommand() {
		assertEquals(
			"LANG=C.UTF-8 LC_ALL=C.UTF-8 mosh-server new -c 256 -l LANG=C.UTF-8 -p 60005:61000",
			MoshConnectParser.buildCommand(60005)
		)
		assertEquals(
			"LANG=C.UTF-8 LC_ALL=C.UTF-8 mosh-server new -c 256 -l LANG=C.UTF-8 -p 60000:61000",
			MoshConnectParser.buildCommand(null)
		)
	}

	@Test
	fun stopServerCommandTargetsPort() {
		val cmd = MoshConnectParser.stopServerCommand(60001)
		assertTrue(cmd.contains("60001"))
		assertTrue(cmd.contains("fuser"))
		assertTrue(cmd.contains("mosh-server"))
		assertTrue(cmd.contains("kill"))
	}

	@Test
	fun missingCommand() {
		assertTrue(MoshConnectParser.looksMissing("bash: mosh-server: command not found", 127))
		assertNull(MoshConnectParser.parse("bash: mosh-server: command not found"))
	}

	@Test
	fun unparsableOutput() {
		assertFalse(MoshConnectParser.looksMissing("mosh-server: weird", 1))
		assertNull(MoshConnectParser.parse("mosh-server started without banner"))
	}
}
