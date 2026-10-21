package com.franzkafkayu.vcserver.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PhoneHostPingTest {
	@Test
	fun pendingNodeIsNotCountedAsLossAndLaterResultIs() = runBlocking {
		val fake = ScriptClient(
			start = """
				{"ok":1,"request_id":"1","nodes":{
				  "us1.node.check-host.net":["us","USA","Los Angeles","1.1.1.1","AS1"]
				}}
			""".trimIndent(),
			results = listOf(
				"""{"us1.node.check-host.net":null}""",
				"""{"us1.node.check-host.net":[[["OK",0.02,"9.9.9.9"],["TIMEOUT",1]]]}"""
			)
		)
		val snaps = mutableListOf<PingRun>()
		engine(local = { 12L }, remote = fake).run("example.com") { snaps += it }
		val pending = snaps.first { it.nodes.singleOrNull()?.pending == true }
		assertNull(pingRowStats(true, false, pending.nodes.single().samples).lossRatio)
		val done = snaps.last().nodes.single()
		assertEquals(0.5, pingRowStats(done.pending, done.resolveFailed, done.samples).lossRatio!!, 0.001)
		assertEquals(listOf(PingSample.Ok(12)), snaps.last().localSamples)
	}

	@Test
	fun remoteFailureKeepsLocalSamples() = runBlocking {
		val fake = ScriptClient(startError = IllegalStateException("http 429"))
		val snaps = mutableListOf<PingRun>()
		engine(local = { 8L }, remote = fake).run("example.com") { snaps += it }
		val last = snaps.last()
		assertEquals(listOf(PingSample.Ok(8)), last.localSamples)
		assertEquals("http 429", last.remoteError)
		assertTrue(last.nodes.isEmpty())
		assertEquals("203.0.113.10", fake.startedIp)
	}

	@Test
	fun localFailureKeepsRemoteRows() = runBlocking {
		val fake = ScriptClient(
			start = """
				{"ok":1,"request_id":"9","nodes":{
				  "de1.node.check-host.net":["de","Germany","Falkenstein","1.1.1.1","AS24940"]
				}}
			""".trimIndent(),
			results = listOf("""{"de1.node.check-host.net":[[["OK",0.05,"9.9.9.9"]]]}""")
		)
		val snaps = mutableListOf<PingRun>()
		engine(local = { throw PingCommandUnavailable() }, remote = fake).run("example.com") { snaps += it }
		val last = snaps.last()
		assertTrue(last.localUnavailable)
		assertEquals(PingSample.Ok(50), last.nodes.single().samples.single())
		assertNull(last.remoteError)
	}

	@Test
	fun resultTimeoutRetriesAndKeepsNodes() = runBlocking {
		val fake = ScriptClient(
			start = """
				{"ok":1,"request_id":"1","nodes":{
				  "us1.node.check-host.net":["us","USA","Dallas","1.1.1.1","AS1"]
				}}
			""".trimIndent(),
			results = listOf("""{"us1.node.check-host.net":[["OK",0.02,"9.9.9.9"]]}"""),
			resultErrors = listOf(java.net.SocketTimeoutException("timeout"))
		)
		val snaps = mutableListOf<PingRun>()
		engine(local = { 8L }, remote = fake).run("example.com") { snaps += it }
		val last = snaps.last()
		assertNull(last.remoteError)
		assertEquals(PingSample.Ok(20), last.nodes.single().samples.single())
	}

	@Test
	fun resolveFailureDoesNotCallCheckHost() = runBlocking {
		val fake = ScriptClient(start = "{}")
		val snaps = mutableListOf<PingRun>()
		PhoneHostPing(
			resolver = { emptyList() },
			localPing = { 1L },
			remote = fake,
			sleeper = { }
		).run("missing.example") { snaps += it }
		assertTrue(snaps.single().resolveFailed)
		assertNull(fake.startedIp)
	}

	private fun engine(local: suspend (InetAddress) -> Long?, remote: CheckHostClient) = PhoneHostPing(
		resolver = { listOf(InetAddress.getByName("203.0.113.10")) },
		localPing = local,
		remote = remote,
		localCount = 1,
		sleeper = { }
	)
}

private class ScriptClient(
	private val start: String = "",
	private val results: List<String> = emptyList(),
	private val startError: Exception? = null,
	private val resultErrors: List<Exception> = emptyList()
) : CheckHostClient {
	var startedIp: String? = null
	private var reads = 0

	override suspend fun start(ip: String): String {
		startedIp = ip
		startError?.let { throw it }
		return start
	}

	override suspend fun result(requestId: String): String {
		if (reads < resultErrors.size) {
			val error = resultErrors[reads]
			reads += 1
			throw error
		}
		val body = results[(reads - resultErrors.size).coerceAtMost(results.lastIndex)]
		reads += 1
		return body
	}
}
