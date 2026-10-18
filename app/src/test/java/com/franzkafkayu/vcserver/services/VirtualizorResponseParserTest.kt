package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualizorResponseParserTest {
	@Test
	fun successListReadsVpsIdIpAndStatus() {
		val result = parseVirtualizorBody(
			"""{"vs":{"12":{"vpsid":"12","hostname":"box","status":"1","ips":{"0":"1.2.3.4"}}}}""",
			powerCall = false
		)
		assertTrue(result.ok)
		assertEquals("12", result.instances.single().id)
		assertEquals("1.2.3.4", result.instances.single().address)
		assertEquals("running", result.instances.single().powerStatus)
	}

	@Test
	fun stoppedStatusMapsToStopped() {
		val result = parseVirtualizorBody(
			"""{"vs":{"8":{"vpsid":8,"status":0}}}""",
			powerCall = false
		)
		assertTrue(result.ok)
		assertEquals("stopped", result.instances.single().powerStatus)
		assertNull(result.instances.single().address)
	}

	@Test
	fun unmappedStatusStaysNull() {
		val result = parseVirtualizorBody(
			"""{"vs":{"8":{"vpsid":"8","status":"weird"}}}""",
			powerCall = false
		)
		assertTrue(result.ok)
		assertNull(result.instances.single().powerStatus)
	}

	@Test
	fun loginFailureIsRejected() {
		val result = parseVirtualizorBody(
			"""{"error":"Invalid password","title":"Login"}""",
			powerCall = false
		)
		assertFalse(result.ok)
		assertTrue(result.instances.isEmpty())
	}

	@Test
	fun powerDoneIsAccepted() {
		val result = parseVirtualizorBody("""{"done":"1"}""", powerCall = true)
		assertTrue(result.ok)
	}

	@Test
	fun bandwidthReadsUsedGbAndUnlimitedLimit() {
		val result = parseVirtualizorBandwidth(
			"""{"act":"bandwidth","bandwidth":{"limit":0,"used":806.9,"limit_gb":0,"used_gb":0.79,"percent":0}}"""
		)
		assertTrue(result.ok)
		assertEquals(0.79, result.bandwidth?.usedGb ?: 0.0, 0.0001)
		assertTrue(result.bandwidth?.unlimited == true)
	}

	@Test
	fun bandwidthLimitedKeepsPercent() {
		val result = parseVirtualizorBandwidth(
			"""{"bandwidth":{"limit":1024,"used":512,"limit_gb":100,"used_gb":12.5,"percent":12}}"""
		)
		assertTrue(result.ok)
		assertEquals(12.5, result.bandwidth?.usedGb ?: 0.0, 0.0001)
		assertEquals(100.0, result.bandwidth?.limitGb ?: 0.0, 0.0001)
		assertEquals(12.0, result.bandwidth?.percent ?: 0.0, 0.0001)
		assertFalse(result.bandwidth?.unlimited == true)
	}

	@Test
	fun bandwidthSumsInOutAsGbAndFormats() {
		val result = parseVirtualizorBandwidth(
			"""{"bandwidth":{"limit_gb":2000,"used_gb":298.089999999,"percent":14.9,"in":{"1":214886.4},"out":{"1":90357.76}}}"""
		)
		assertTrue(result.ok)
		assertEquals("298.09", formatBandwidthGb(result.bandwidth!!.usedGb!!))
		assertEquals("2000", formatBandwidthGb(result.bandwidth!!.limitGb!!))
		assertEquals("14.9", formatBandwidthPercent(result.bandwidth!!.percent!!, 1))
		assertEquals(209.85, result.bandwidth!!.inGb ?: 0.0, 0.01)
		assertEquals(88.24, result.bandwidth!!.outGb ?: 0.0, 0.01)
	}
}
