package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreencloudClientTest {
	@Test
	fun parsesPaginatedServerList() {
		val result = parseGreencloudBody(
			"""{"data":[{"id":12,"name":"VPS","hostname":"box","network":{"primary":{"ipv4":[{"address":"1.2.3.4"}]}}}]}"""
		)
		assertTrue(result.ok)
		assertEquals("12", result.instances.single().id)
		assertEquals("1.2.3.4", result.instances.single().address)
		assertEquals("VPS", result.instances.single().label)
	}

	@Test
	fun parsesServerDetailsAndTraffic() {
		val result = parseGreencloudBody(
			"""
			{"data":{"id":"6b32bae9-83e9-44f9-afd4-244f1fa27a49","name":"Test VM","hostname":null,"suspended":false,"rescue":false,"vncEnabled":false,"memory":"1024 MB","cpu":"2 Core","storage":[{"capacity":"10 GB","enabled":true,"primary":true}],"network":{"primary":{"limit":"20000 GB","ipv4":[{"address":"10.5.5.6"}]},"secondary":{"ipv4":[{"address":"10.40.40.2"}]}},"state":{"status":"running","running":true,"network":{"primary":{"traffic":{"rx":1,"tx":1,"total":154390929}}}}}}
			""".trimIndent()
		)
		assertTrue(result.ok)
		assertEquals("running", result.powerStatus)
		val info = result.info!!
		assertEquals("Test VM", info.name)
		assertEquals("2 Core", info.cpu)
		assertEquals("1024 MB", info.ram)
		assertEquals("10 GB", info.disk)
		assertEquals(listOf("10.5.5.6", "10.40.40.2"), info.ipAddresses)
		assertEquals(false, info.rescue)
		assertTrue(info.trafficUsed!!.contains("MB") || info.trafficUsed!!.contains("GB"))
		assertEquals("20000 GB", info.trafficLimit)
	}

	@Test
	fun parsesResetPassword() {
		val result = parseGreencloudBody(
			"""{"data":{"task":{"id":2892,"status":"accepted"},"expectedPassword":"631ZganA8zEVe5UhjxQF"}}"""
		)
		assertTrue(result.ok)
		assertEquals("631ZganA8zEVe5UhjxQF", result.expectedPassword)
	}

	@Test
	fun parsesServerState() {
		val result = parseGreencloudBody(
			"""{"data":{"id":12,"state":{"status":"running","running":true}}}"""
		)
		assertTrue(result.ok)
		assertEquals("running", result.powerStatus)
	}

	@Test
	fun unauthorizedIsNotOk() {
		val result = parseGreencloudBody("", 401)
		assertFalse(result.ok)
		assertEquals("unauthorized", result.message)
	}

	@Test
	fun errorsArrayIsNotOk() {
		val result = parseGreencloudBody("""{"errors":["bad token"]}""", 422)
		assertFalse(result.ok)
		assertTrue(result.message!!.contains("bad token"))
	}

	@Test
	fun emptyErrorBodyFails() {
		val result = parseGreencloudBody("  ", 500)
		assertFalse(result.ok)
	}
}
