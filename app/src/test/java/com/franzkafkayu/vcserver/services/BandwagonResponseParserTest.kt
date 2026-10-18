package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandwagonResponseParserTest {
	@Test
	fun errorZeroIsSuccess() {
		val parsed = parseBandwagonResponse("""{"error":0}""")
		assertEquals(0, parsed.error)
		assertNull(parsed.message)
		assertNull(parsed.veStatus)
	}

	@Test
	fun nonZeroErrorKeepsMessage() {
		val parsed = parseBandwagonResponse("""{"error":5,"message":"invalid key"}""")
		assertEquals(5, parsed.error)
		assertEquals("invalid key", parsed.message)
	}

	@Test
	fun keepsVeStatusAndIgnoresScreenshot() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"ve_status":"Running","screendump_png_base64":"abc"}"""
		)
		assertEquals(0, parsed.error)
		assertEquals("Running", parsed.veStatus)
	}

	@Test
	fun missingVeStatusStaysEmpty() {
		val parsed = parseBandwagonResponse("""{"error":0,"vz_status":{"nproc":"1"}}""")
		assertEquals(0, parsed.error)
		assertNull(parsed.veStatus)
	}

	@Test
	fun serviceInfoReadsOfficialSampleAndSkipsEmail() {
		val parsed = parseBandwagonResponse(
			"""
			{"hostname":"my.server.com","node_alias":"Node32","node_location":"US, Florida",
			"plan":"micro128","plan_monthly_data":322122547200,"plan_disk":4294967296,
			"plan_ram":155189248,"os":"centos-6-x86_64","email":"customer@example.com",
			"data_counter":569810827,"data_next_reset":1430193600,
			"ip_addresses":["11.22.33.44","11.22.33.45"],
			"ptr":{"11.22.33.44":"ns1.my.server.com"},"error":0}
			""".trimIndent()
		)
		assertEquals(0, parsed.error)
		val info = parsed.info!!
		assertEquals("my.server.com", info.hostname)
		assertEquals("US, Florida", info.nodeLocation)
		assertEquals("micro128", info.plan)
		assertEquals(322122547200L, info.monthlyDataBytes)
		assertEquals(569810827L, info.dataCounter)
		assertEquals(1430193600L, info.dataNextReset)
		assertEquals(4294967296L, info.planDisk)
		assertEquals(155189248L, info.planRam)
		assertEquals("centos-6-x86_64", info.os)
		assertEquals(listOf("11.22.33.44", "11.22.33.45"), info.ipAddresses)
		assertEquals("300 GB", formatBandwagonBytes(322122547200L))
	}

	@Test
	fun missingServiceFieldsStayNull() {
		val parsed = parseBandwagonResponse("""{"error":0}""")
		assertNull(parsed.info?.hostname)
		assertTrue(parsed.info?.ipAddresses?.isEmpty() == true)
	}

	@Test
	fun nonZeroErrorDoesNotParseInfo() {
		val parsed = parseBandwagonResponse("""{"error":5,"message":"no","hostname":"x"}""")
		assertNull(parsed.info)
	}
}
