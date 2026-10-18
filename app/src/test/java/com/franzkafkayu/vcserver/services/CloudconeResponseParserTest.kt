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

	@Test
	fun informationReadsWrappedDetails() {
		val result = parseCloudconeBody(
			"""
			{"status":1,"message":"ok","__data":{
			"id":245123,"hostname":"box.example","ips":["203.0.113.10","2001:db8::1"],
			"os":{"name":"Ubuntu 22.04"},"cpu":2,"ram":2048,"disk":40,
			"bandwidth":{"used":12,"total":1000},"node":{"name":"LA"},
			"state":{"name":"active"}
			}}
			""".trimIndent()
		)
		assertTrue(result.ok)
		val info = result.info!!
		assertEquals("box.example", info.hostname)
		assertEquals(listOf("203.0.113.10", "2001:db8::1"), info.ipAddresses)
		assertEquals("Ubuntu 22.04", info.os)
		assertEquals("2", info.cpu)
		assertEquals("2048", info.ram)
		assertEquals("40", info.disk)
		assertEquals("12", info.bandwidthUsed)
		assertEquals("1000", info.bandwidthTotal)
		assertEquals("LA", info.node)
		assertEquals("active", info.powerStatus)
	}

	@Test
	fun informationOmitsMissingFields() {
		val result = parseCloudconeBody("""{"status":1,"data":{"id":8,"hostname":"only"}}""")
		assertTrue(result.ok)
		val info = result.info!!
		assertEquals("only", info.hostname)
		assertTrue(info.ipAddresses.isEmpty())
		assertNull(info.os)
		assertNull(info.cpu)
	}

	@Test
	fun informationFailureHasNoInfo() {
		val result = parseCloudconeBody("""{"status":0,"message":"denied"}""")
		assertFalse(result.ok)
		assertNull(result.info)
	}

	@Test
	fun informationReadsCapitalizedAndNestedInstance() {
		val result = parseCloudconeBody(
			"""
			{"status":1,"message":"ok","__data":{"Instance":{
			"ID":77,"Hostname":"cc-la","IPs":"117.55.224.61","OS":"Debian GNU/Linux",
			"CPU":1,"RAM":1024,"Disk":20,"Node":"Los Angeles","Power":"online"
			}}}
			""".trimIndent()
		)
		val info = result.info!!
		assertEquals("cc-la", info.hostname)
		assertEquals(listOf("117.55.224.61"), info.ipAddresses)
		assertEquals("Debian GNU/Linux", info.os)
		assertEquals("1", info.cpu)
		assertEquals("1024", info.ram)
		assertEquals("Los Angeles", info.node)
		assertEquals("online", info.powerStatus)
	}

	@Test
	fun informationReadsDataJsonString() {
		val result = parseCloudconeBody(
			"""{"status":1,"__data":"{\"hostname\":\"cc-sc2\",\"ip\":\"1.2.3.4\",\"os\":\"Ubuntu\"}"}"""
		)
		assertEquals("cc-sc2", result.info!!.hostname)
		assertEquals(listOf("1.2.3.4"), result.info!!.ipAddresses)
	}

	@Test
	fun informationReadsInstancesObjectPayload() {
		val result = parseCloudconeBody(
			"""
			{"status":1,"message":"Instance retrieved","__data":{"instances":{
			"hostname":"box.example","label":"box.example","status":"online",
			"template":"Ubuntu 22.04 LTS Server - 64 Bit","mainip":"203.0.113.10",
			"ips":[{"id":1,"address":"203.0.113.10","ipv4":true},{"id":2,"address":"2001:db8::1","ipv4":false}],
			"disk":{"total":60,"used":false},"ram":2,"cpu":1,"node":129,"nodename":"S99",
			"location":{"location_nice_name":"Los Angeles, CA - DC1"},
			"bandwidth":{"used":12.5,"total":5},
			"state":{"name":"online","color":"success"}
			}}}
			""".trimIndent()
		)
		val info = result.info!!
		assertEquals("box.example", info.hostname)
		assertEquals(listOf("203.0.113.10", "2001:db8::1"), info.ipAddresses)
		assertEquals("Ubuntu 22.04 LTS Server - 64 Bit", info.os)
		assertEquals("1", info.cpu)
		assertEquals("2", info.ram)
		assertEquals("60", info.disk)
		assertEquals("12.5", info.bandwidthUsed)
		assertEquals("5", info.bandwidthTotal)
		assertEquals("Los Angeles, CA - DC1", info.node)
		assertEquals("online", info.powerStatus)
	}
}
