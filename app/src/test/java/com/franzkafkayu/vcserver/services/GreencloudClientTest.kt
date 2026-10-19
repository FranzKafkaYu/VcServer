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
