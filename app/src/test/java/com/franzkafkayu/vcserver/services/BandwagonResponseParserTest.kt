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
		assertNull(parsed.migrateLocations)
		assertNull(parsed.migrateStart)
		assertNull(parsed.password)
		assertNull(parsed.usage)
	}

	@Test
	fun migrateLocationsReadsOfficialFields() {
		val parsed = parseBandwagonResponse(
			"""
			{"error":0,"currentLocation":"USCA_9",
			"locations":["USCA_9","USFL_2"],
			"descriptions":{"USCA_9":"Los Angeles","USFL_2":"Miami, Florida"},
			"dataTransferMultipliers":{"USFL_2":0.5}}
			""".trimIndent()
		)
		val locations = parsed.migrateLocations!!
		assertEquals("USCA_9", locations.currentLocation)
		assertEquals(2, locations.options.size)
		assertEquals("Los Angeles", locations.options[0].description)
		assertEquals(0.5, locations.options[1].dataTransferMultiplier!!, 0.0)
		assertNull(locations.options[0].dataTransferMultiplier)
	}

	@Test
	fun missingMigrateLocationsStayNull() {
		val parsed = parseBandwagonResponse("""{"error":0}""")
		assertNull(parsed.migrateLocations)
		assertNull(parsed.migrateStart)
	}

	@Test
	fun migrateStartReadsEmailAndIps() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"notificationEmail":"user@example.com","newIps":["11.22.33.44","11.22.33.45"]}"""
		)
		val start = parsed.migrateStart!!
		assertEquals("user@example.com", start.notificationEmail)
		assertEquals(listOf("11.22.33.44", "11.22.33.45"), start.newIps)
	}

	@Test
	fun migrateStartFailureSkipsPayload() {
		val parsed = parseBandwagonResponse(
			"""{"error":7,"message":"busy","notificationEmail":"x","newIps":["1.1.1.1"]}"""
		)
		assertNull(parsed.migrateStart)
	}

	@Test
	fun resetPasswordReadsField() {
		val parsed = parseBandwagonResponse("""{"error":0,"password":"n3w-secret"}""")
		assertEquals("n3w-secret", parsed.password)
	}

	@Test
	fun usageObjectSeriesAndRangeSlice() {
		val parsed = parseBandwagonResponse(
			"""
			{"error":0,"data":{
			"cpu_usage":[[1700000000,1.5],[1700003600,2.0]],
			"network_in_bytes":[[1700000000,100],[1700003600,1100]],
			"network_out_bytes":[[1700000000,50],[1700003600,250]]
			}}
			""".trimIndent()
		)
		val usage = parsed.usage!!
		assertEquals(2, usage.cpuUsage.size)
		assertEquals(2.0, usage.cpuUsage[1].value, 0.0)
		assertEquals(1, usage.networkInBps.size)
		assertEquals(1000.0 * 8.0 / 3600.0, usage.networkInBps[0].value, 0.001)
		val sliced = sliceBandwagonUsage(usage, BandwagonUsageRange.DAY, nowSec = 1700003600L)
		assertEquals(2, sliced.cpuUsage.size)
		val empty = sliceBandwagonUsage(usage, BandwagonUsageRange.DAY, nowSec = 1800000000L)
		assertTrue(empty.cpuUsage.isEmpty())
	}

	@Test
	fun usageRowArrayReadsCpuAndNetwork() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"data":[[1700000000,3,10,20],[1700000060,4,40,80]]}"""
		)
		val usage = parsed.usage!!
		assertEquals(3.0, usage.cpuUsage[0].value, 0.0)
		assertEquals(10.0, usage.networkInBytes[0].value, 0.0)
		assertEquals(20.0, usage.networkOutBytes[0].value, 0.0)
		assertTrue(usage.networkInBps.isNotEmpty())
	}

	@Test
	fun missingUsageStaysNull() {
		val parsed = parseBandwagonResponse("""{"error":0}""")
		assertNull(parsed.usage)
		assertNull(parsed.password)
	}

	@Test
	fun usageObjectMapOfTimestamps() {
		val parsed = parseBandwagonResponse(
			"""
			{"error":0,"data":{
			"cpu_usage":{"1700000000":1.5,"1700003600":2.0},
			"network_in_bytes":{"1700000000":100,"1700003600":1100},
			"network_out_bytes":{"1700000000":50,"1700003600":250}
			}}
			""".trimIndent()
		)
		val usage = parsed.usage!!
		assertEquals(2, usage.cpuUsage.size)
		assertEquals(1.5, usage.cpuUsage[0].value, 0.0)
		assertEquals(2, usage.networkInBytes.size)
		assertTrue(usage.networkInBps.isNotEmpty())
	}

	@Test
	fun usageRootLevelSeriesWithoutDataWrapper() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"cpu_usage":{"1700000000":3.0},"network_in_bytes":{"1700000000":9}}"""
		)
		val usage = parsed.usage!!
		assertEquals(3.0, usage.cpuUsage[0].value, 0.0)
		assertEquals(9.0, usage.networkInBytes[0].value, 0.0)
	}

	@Test
	fun usageArrayOfNamedObjects() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"data":[{"timestamp":1700000000,"cpu_usage":1,"network_in_bytes":10,"network_out_bytes":20}]}"""
		)
		val usage = parsed.usage!!
		assertEquals(1.0, usage.cpuUsage[0].value, 0.0)
		assertEquals(10.0, usage.networkInBytes[0].value, 0.0)
	}

	@Test
	fun usageTimestampKeyedObjectsWithShortFieldNames() {
		val parsed = parseBandwagonResponse(
			"""
			{"error":0,"data":{
			"1700000000":{"cpu":1.5,"net_in":100,"net_out":50},
			"1700003600":{"cpu":2.0,"net_in":1100,"net_out":250}
			}}
			""".trimIndent()
		)
		val usage = parsed.usage!!
		assertEquals(2, usage.cpuUsage.size)
		assertEquals(1.5, usage.cpuUsage[0].value, 0.0)
		assertEquals(1100.0, usage.networkInBytes[1].value, 0.0)
		assertTrue(usage.networkInBps.isNotEmpty())
	}

	@Test
	fun usageNamedSeriesWithShortAliases() {
		val parsed = parseBandwagonResponse(
			"""{"error":0,"data":{"cpu":{"1700000000":4},"net_in":{"1700000000":8},"net_out":{"1700000000":3}}}"""
		)
		val usage = parsed.usage!!
		assertEquals(4.0, usage.cpuUsage[0].value, 0.0)
		assertEquals(8.0, usage.networkInBytes[0].value, 0.0)
		assertEquals(3.0, usage.networkOutBytes[0].value, 0.0)
	}
}
