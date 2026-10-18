package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandwagonPowerCoordinatorTest {
	@Test
	fun bindSucceedsOnlyAfterVendorAccepts() = runBlocking {
		val harness = Harness()
		harness.client.next = ok()
		val result = harness.coordinator.bind(1L, "200", "secret")
		assertTrue(result is BandwagonBindResult.Success)
		assertEquals("200", harness.store.getByServerId(1L)?.veid)
		assertEquals(listOf(HttpBandwagonClient.ACTION_INFO), harness.client.calls)
	}

	@Test
	fun bindFailureDoesNotSave() = runBlocking {
		val harness = Harness()
		harness.client.next = BandwagonCallResult(error = 3, message = "no", veStatus = null)
		val result = harness.coordinator.bind(1L, "200", "secret")
		assertTrue(result is BandwagonBindResult.Failed)
		assertNull(harness.store.getByServerId(1L))
		assertTrue(harness.keys.live.isEmpty())
	}

	@Test
	fun veidUsedByAnotherServerIsRejected() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 2L, veid = "200", encryptedApiKey = "old"))
		val result = harness.coordinator.bind(1L, "200", "secret")
		assertTrue(result is BandwagonBindResult.VeidInUse)
		assertTrue(harness.client.calls.isEmpty())
		assertEquals(2L, harness.store.getByVeid("200")?.serverId)
		assertNull(harness.store.getByServerId(1L))
	}

	@Test
	fun replaceDeletesPreviousKey() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(id = 4, serverId = 1L, veid = "100", encryptedApiKey = "old-key"))
		harness.keys.live["old-key"] = "previous"
		harness.client.next = ok()
		val result = harness.coordinator.bind(1L, "300", "next")
		assertTrue(result is BandwagonBindResult.Success)
		assertEquals("300", harness.store.getByServerId(1L)?.veid)
		assertTrue(harness.keys.deleted.contains("old-key"))
		assertEquals("next", harness.keys.live[harness.store.getByServerId(1L)?.encryptedApiKey])
	}

	@Test
	fun stopWithoutConfirmationDoesNotCallVendor() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key"))
		harness.keys.live["key"] = "secret"
		val result = harness.coordinator.power(1L, BandwagonPowerAction.STOP, confirmed = false)
		assertTrue(result is BandwagonPowerResult.NeedsConfirmation)
		assertTrue(harness.client.calls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun acceptedStopClosesPooledSession() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key"))
		harness.keys.live["key"] = "secret"
		harness.client.next = ok()
		val result = harness.coordinator.power(1L, BandwagonPowerAction.STOP, confirmed = true)
		assertTrue(result is BandwagonPowerResult.Accepted)
		assertEquals(listOf(HttpBandwagonClient.ACTION_STOP), harness.client.calls)
		assertEquals(listOf(1L), harness.closed)
	}

	@Test
	fun failedStopKeepsBindingAndSession() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key"))
		harness.keys.live["key"] = "secret"
		harness.client.next = BandwagonCallResult(error = 1, message = "busy", veStatus = null)
		val result = harness.coordinator.power(1L, BandwagonPowerAction.STOP, confirmed = true)
		assertTrue(result is BandwagonPowerResult.Failed)
		assertEquals("200", harness.store.getByServerId(1L)?.veid)
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun deletingServerRemovesOnlyThatLink() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key-1"))
		harness.store.upsert(BandwagonLink(serverId = 2L, veid = "201", encryptedApiKey = "key-2"))
		harness.coordinator.onServerDeleted(1L)
		assertNull(harness.store.getByServerId(1L))
		assertEquals("201", harness.store.getByServerId(2L)?.veid)
		assertTrue(harness.keys.deleted.contains("key-1"))
	}

	@Test
	fun serviceInfoRequiresBindingAndKeepsLinkOnFailure() = runBlocking {
		val harness = Harness()
		val unbound = harness.coordinator.serviceInfo(1L)
		assertTrue(unbound is BandwagonServiceInfoResult.NotBound)
		assertTrue(harness.client.calls.isEmpty())

		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key"))
		harness.keys.live["key"] = "secret"
		harness.client.next = BandwagonCallResult(
			error = 0,
			message = null,
			veStatus = null,
			info = BandwagonServiceInfo(
				hostname = "box",
				nodeLocation = "US, Florida",
				plan = "micro128",
				monthlyDataBytes = 100L,
				dataCounter = 10L,
				dataNextReset = 1L,
				planDisk = 2L,
				planRam = 3L,
				os = "centos",
				ipAddresses = listOf("1.1.1.1")
			)
		)
		val known = harness.coordinator.serviceInfo(1L)
		assertTrue(known is BandwagonServiceInfoResult.Known)
		assertEquals("box", (known as BandwagonServiceInfoResult.Known).info.hostname)
		assertEquals(listOf(HttpBandwagonClient.ACTION_INFO), harness.client.calls)

		harness.client.next = BandwagonCallResult(error = 1, message = "busy", veStatus = null)
		val failed = harness.coordinator.serviceInfo(1L)
		assertTrue(failed is BandwagonServiceInfoResult.Failed)
		assertEquals("200", harness.store.getByServerId(1L)?.veid)
	}

	@Test
	fun duplicateDoesNotCopyLink() = runBlocking {
		val harness = Harness()
		harness.store.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "key"))
		val copiedServerId = 9L
		assertNull(harness.store.getByServerId(copiedServerId))
		assertEquals("200", harness.store.getByServerId(1L)?.veid)
	}

	private fun ok() = BandwagonCallResult(error = 0, message = null, veStatus = null)

	private class Harness {
		val store = MemoryBandwagonLinkStore()
		val client = FakeBandwagonClient()
		val keys = FakeBandwagonKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = BandwagonPowerCoordinator(store, client, keys) { closed += it }
	}
}

private class FakeBandwagonClient : BandwagonClient {
	val calls = mutableListOf<String>()
	var next = BandwagonCallResult(error = 0, message = null, veStatus = null)

	override suspend fun call(
		action: String,
		veid: String,
		apiKey: String,
		timeoutMs: Int
	): BandwagonCallResult {
		calls += action
		return next
	}
}

private class FakeBandwagonKeyStore : BandwagonKeyStore {
	val live = mutableMapOf<String, String>()
	val deleted = mutableListOf<String>()
	private var sequence = 0

	override fun encrypt(apiKey: String): String {
		val path = "enc-${sequence++}"
		live[path] = apiKey
		return path
	}

	override fun decrypt(path: String): String = live.getValue(path)

	override fun delete(path: String) {
		deleted += path
		live.remove(path)
	}
}

private class MemoryBandwagonLinkStore : BandwagonLinkStore {
	private val links = linkedMapOf<Long, BandwagonLink>()
	private val ids = MutableStateFlow<Set<Long>>(emptySet())

	override suspend fun getByServerId(serverId: Long): BandwagonLink? = links[serverId]

	override suspend fun getByVeid(veid: String): BandwagonLink? = links.values.find { it.veid == veid }

	override suspend fun upsert(link: BandwagonLink) {
		links[link.serverId] = link
		ids.value = links.keys.toSet()
	}

	override suspend fun deleteByServerId(serverId: Long) {
		links.remove(serverId)
		ids.value = links.keys.toSet()
	}

	override fun observeBoundServerIds(): Flow<Set<Long>> = ids.map { it }
}
