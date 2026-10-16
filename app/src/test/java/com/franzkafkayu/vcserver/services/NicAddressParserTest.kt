package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NicAddressParserTest {
	private val sample = """
		1: lo    inet 127.0.0.1/8 scope host lo\       valid_lft forever preferred_lft forever
		2: eth0    inet 104.168.47.110/24 brd 104.168.47.255 scope global eth0\       valid_lft forever preferred_lft forever
		2: eth0    inet 10.0.0.8/24 scope global secondary eth0\       valid_lft forever preferred_lft forever
		2: eth0    inet6 2001:db8::1/64 scope global \       valid_lft forever preferred_lft forever
		2: eth0    inet6 fd00::8/64 scope global \       valid_lft forever preferred_lft forever
		2: eth0    inet6 fe80::1/64 scope link \       valid_lft forever preferred_lft forever
		3: docker0    inet 172.17.0.1/16 scope global docker0\       valid_lft forever preferred_lft forever
	""".trimIndent()

	@Test
	fun keepsIpv4AndGlobalIpv6WithPrefix() {
		val eth0 = parseNicAddresses(sample).getValue("eth0")
		assertEquals(listOf("104.168.47.110/24", "10.0.0.8/24"), eth0.ipv4)
		assertEquals(listOf("2001:db8::1/64", "fd00::8/64"), eth0.ipv6)
	}

	@Test
	fun dropsLinkLocalIpv6AndKeepsOtherInterfaces() {
		val parsed = parseNicAddresses(sample)
		assertTrue(parsed.getValue("eth0").ipv6.none { it.startsWith("fe80::") })
		assertEquals(listOf("172.17.0.1/16"), parsed.getValue("docker0").ipv4)
	}

	@Test
	fun emptyOutputHasNoAddresses() {
		assertTrue(parseNicAddresses("").isEmpty())
		assertTrue(parseNicAddresses("\n  \n").isEmpty())
	}
}
