package com.franzkafkayu.vcserver.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MergeNetworkInfoTest {
	@Test
	fun incomingNullKeepsPrevious() {
		val previous = NetworkInfo(1, 2, listOf(nic("eth0", ipv4 = listOf("1.1.1.1"))))
		assertSame(previous, mergeNetworkInfo(previous, null))
	}

	@Test
	fun previousNullUsesIncoming() {
		val incoming = NetworkInfo(3, 0, emptyList())
		assertSame(incoming, mergeNetworkInfo(null, incoming))
	}

	@Test
	fun bothNullIsNull() {
		assertNull(mergeNetworkInfo(null, null))
	}

	@Test
	fun emptyIncomingInterfacesKeepPreviousNics() {
		val previous = NetworkInfo(9, 1, listOf(nic("eth0", rx = 10, ipv4 = listOf("1.1.1.1"))))
		val incoming = NetworkInfo(4, 0, emptyList())
		val merged = mergeNetworkInfo(previous, incoming)!!
		assertEquals(4, merged.tcpConnections)
		assertEquals(0, merged.udpConnections)
		assertEquals(listOf("eth0"), merged.interfaces.map { it.name })
		assertEquals(10L, merged.interfaces.single().totalRxBytes)
		assertEquals(listOf("1.1.1.1"), merged.interfaces.single().ipv4Addresses)
	}

	@Test
	fun emptyAddressesReusePreviousOnSameNic() {
		val previous = nic("eth0", ipv4 = listOf("1.1.1.1/24"), ipv6 = listOf("2001:db8::1/64"), rx = 1)
		val incoming = nic("eth0", ipv4 = emptyList(), ipv6 = emptyList(), rx = 99, rxRate = 5.0)
		val merged = mergeNetworkInfo(
			NetworkInfo(1, 0, listOf(previous)),
			NetworkInfo(2, 1, listOf(incoming))
		)!!
		val eth0 = merged.interfaces.single()
		assertEquals(99L, eth0.totalRxBytes)
		assertEquals(5.0, eth0.currentRxRate, 0.0)
		assertEquals(listOf("1.1.1.1/24"), eth0.ipv4Addresses)
		assertEquals(listOf("2001:db8::1/64"), eth0.ipv6Addresses)
	}

	@Test
	fun unionKeepsPreviousOnlyNicAndAddsNew() {
		val previous = NetworkInfo(1, 0, listOf(nic("eth0", ipv4 = listOf("10.0.0.1"))))
		val incoming = NetworkInfo(2, 0, listOf(nic("eth1", ipv4 = listOf("10.0.0.2"))))
		val merged = mergeNetworkInfo(previous, incoming)!!
		assertEquals(listOf("eth1", "eth0"), merged.interfaces.map { it.name })
	}

	@Test
	fun nonemptyIncomingAddressesReplacePrevious() {
		val previous = nic("eth0", ipv4 = listOf("old"))
		val incoming = nic("eth0", ipv4 = listOf("new"))
		val merged = mergeNetworkInfo(
			NetworkInfo(0, 0, listOf(previous)),
			NetworkInfo(0, 0, listOf(incoming))
		)!!
		assertEquals(listOf("new"), merged.interfaces.single().ipv4Addresses)
	}

	private fun nic(
		name: String,
		rx: Long = 0,
		rxRate: Double = 0.0,
		ipv4: List<String> = emptyList(),
		ipv6: List<String> = emptyList()
	) = NetworkInterfaceInfo(
		name = name,
		totalRxBytes = rx,
		totalTxBytes = 0,
		currentRxRate = rxRate,
		currentTxRate = 0.0,
		ipv4Addresses = ipv4,
		ipv6Addresses = ipv6
	)
}
