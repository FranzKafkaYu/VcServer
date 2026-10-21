package com.franzkafkayu.vcserver.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckHostPingParserTest {
	private val start = """
		{"ok":1,"request_id":"806df9","nodes":{
		  "us1.node.check-host.net":["us","USA","Los Angeles","5.253.30.82","AS18978"],
		  "de1.node.check-host.net":["de","Germany","","46.4.143.48","AS24940"]
		}}
	""".trimIndent()

	@Test
	fun startReadsPlaceAndAsn() {
		val request = parseCheckHostRequest(start)
		assertEquals("806df9", request.requestId)
		assertEquals("USA, Los Angeles", request.nodes[0].place)
		assertEquals("AS18978", request.nodes[0].line)
		assertEquals("Germany", request.nodes[1].place)
		assertTrue(request.nodes.all { it.pending })
	}

	@Test
	fun okTimeoutAndMalformed() {
		val nodes = applyCheckHostResult(
			parseCheckHostRequest(start).nodes,
			"""
			{"us1.node.check-host.net":[[
			  ["OK",0.044,"1.2.3.4"],
			  ["TIMEOUT",3.005],
			  ["MALFORMED",0.045],
			  ["OK",0.010]
			]]}
			""".trimIndent()
		)
		val row = nodes.first { it.id.startsWith("us1") }
		assertEquals(
			listOf(PingSample.Ok(44), PingSample.Failed, PingSample.Failed, PingSample.Ok(10)),
			row.samples
		)
		val stats = pingRowStats(row.pending, row.resolveFailed, row.samples)
		assertEquals(4, stats.finished)
		assertEquals(2, stats.failed)
		assertEquals(0.5, stats.lossRatio!!, 0.001)
		assertEquals(17.0, stats.jitterMs!!, 0.001)
	}

	@Test
	fun pendingNodeIsNotLoss() {
		val nodes = applyCheckHostResult(
			parseCheckHostRequest(start).nodes,
			"""{"us1.node.check-host.net":null}"""
		)
		val row = nodes.first { it.id.startsWith("us1") }
		assertTrue(row.pending)
		val stats = pingRowStats(row.pending, row.resolveFailed, row.samples)
		assertNull(stats.lossRatio)
		assertEquals(0, stats.finished)
	}

	@Test
	fun nullProbeIsResolveFailure() {
		val nodes = applyCheckHostResult(
			parseCheckHostRequest(start).nodes,
			"""{"de1.node.check-host.net":[[null]]}"""
		)
		val row = nodes.first { it.id.startsWith("de1") }
		assertTrue(row.resolveFailed)
		assertNull(pingRowStats(row.pending, row.resolveFailed, row.samples).lossRatio)
	}

	@Test
	fun officialSingleWrapProbes() {
		val nodes = applyCheckHostResult(
			parseCheckHostRequest(start).nodes,
			"""
			{"us1.node.check-host.net":[
			  ["OK",0.044,"1.2.3.4"],
			  ["TIMEOUT",3.005],
			  ["MALFORMED",0.045],
			  ["OK","0.010"]
			]}
			""".trimIndent()
		)
		val row = nodes.first { it.id.startsWith("us1") }
		assertEquals(
			listOf(PingSample.Ok(44), PingSample.Failed, PingSample.Failed, PingSample.Ok(10)),
			row.samples
		)
	}

	@Test
	fun nullResultKeepsPending() {
		val nodes = applyCheckHostResult(parseCheckHostRequest(start).nodes, "null")
		assertTrue(nodes.all { it.pending })
	}

	@Test
	fun singleSuccessHasNoJitter() {
		val stats = pingRowStats(
			pending = false,
			resolveFailed = false,
			samples = listOf(PingSample.Ok(44))
		)
		assertNull(stats.jitterMs)
		assertEquals(0.0, stats.lossRatio!!, 0.001)
	}
}
