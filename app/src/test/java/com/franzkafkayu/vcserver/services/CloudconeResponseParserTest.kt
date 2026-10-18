package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudconeResponseParserTest {
	@Test
	fun successListReadsInstances() {
		val result = parseCloudconeBody(
			"""{"instances":[{"id":"12","ip":"1.2.3.4","status":"Running"}]}"""
		)
		assertTrue(result.ok)
		assertEquals("12", result.instances.single().id)
		assertEquals("1.2.3.4", result.instances.single().address)
		assertEquals("Running", result.instances.single().powerStatus)
	}

	@Test
	fun wrappedDataListIsAccepted() {
		val result = parseCloudconeBody(
			"""{"__data":{"instances":[{"id":"9","ips":["10.0.0.8"]}]}}"""
		)
		assertTrue(result.ok)
		assertEquals("10.0.0.8", result.instances.single().address)
		assertNull(result.instances.single().powerStatus)
	}

	@Test
	fun explicitFailureWithoutInstancesFails() {
		val result = parseCloudconeBody("""{"status":0,"message":"bad hash"}""")
		assertFalse(result.ok)
		assertEquals("bad hash", result.message)
		assertTrue(result.instances.isEmpty())
	}

	@Test
	fun emptyInstanceListIsSuccess() {
		val result = parseCloudconeBody("""{"instances":[]}""")
		assertTrue(result.ok)
		assertTrue(result.instances.isEmpty())
	}

	@Test
	fun officialListShapeReadsIdAddressAndState() {
		val result = parseCloudconeBody(
			"""
			{"status":1,"message":"Instances retrieved","__data":{"instances":[
				{"id":245123,"ips":"203.0.113.10","hostname":"box","state":{"name":"active","color":"success","id":1}}
			],"total_instances":1}}
			""".trimIndent()
		)
		assertTrue(result.ok)
		assertEquals("Instances retrieved", result.message)
		assertEquals("245123", result.instances.single().id)
		assertEquals("203.0.113.10", result.instances.single().address)
		assertEquals("active", result.instances.single().powerStatus)
	}

	@Test
	fun missingAddressAndPowerStayNull() {
		val result = parseCloudconeBody("""{"instances":[{"id":"3","hostname":"box"}]}""")
		assertTrue(result.ok)
		assertNull(result.instances.single().address)
		assertNull(result.instances.single().powerStatus)
	}
}
