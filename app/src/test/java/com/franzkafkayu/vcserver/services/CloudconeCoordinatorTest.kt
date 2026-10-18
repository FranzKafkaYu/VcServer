package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudconeCoordinatorTest {
	@Test
	fun listRequiresKeyAndHash() = runBlocking {
		val harness = Harness()
		val failed = harness.coordinator.listInstances("", "hash")
		assertTrue(!failed.ok)
		assertTrue(harness.client.listCalls.isEmpty())
	}

	@Test
	fun unbindDeletesKeyFiles() = runBlocking {
		val harness = Harness()
		harness.client.listResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		assertTrue(harness.coordinator.bind(4L, "9", "secret", "digest") is CloudconeBindResult.Success)
		val link = harness.links.getByServerId(4L)!!
		harness.coordinator.unbind(4L)
		assertTrue(harness.links.all().isEmpty())
		assertTrue(harness.keys.deleted.contains(link.encryptedApiKey))
		assertTrue(harness.keys.deleted.contains(link.encryptedHash))
	}

	@Test
	fun bindRejectsOtherVendorAndDuplicateInstance() = runBlocking {
		val harness = Harness()
		harness.bandwagon.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "bw"))
		assertTrue(harness.coordinator.bind(1L, "9", "k", "h") is CloudconeBindResult.OtherVendor)
		assertTrue(harness.links.all().isEmpty())

		harness.bandwagon.deleteByServerId(1L)
		harness.links.insert(sampleLink(serverId = 2L, instanceId = "9"))
		assertTrue(harness.coordinator.bind(1L, "9", "k", "h") is CloudconeBindResult.InstanceInUse)
	}

	@Test
	fun secondBindUsesOwnCredentials() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.bind(1L, "9", "key-a", "hash-a") is CloudconeBindResult.Success)
		assertTrue(harness.coordinator.bind(2L, "10", "key-b", "hash-b") is CloudconeBindResult.Success)
		assertEquals("key-a", harness.keys.live[harness.links.getByServerId(1L)?.encryptedApiKey])
		assertEquals("key-b", harness.keys.live[harness.links.getByServerId(2L)?.encryptedApiKey])
	}

	@Test
	fun bandwagonBindRejectsCloudconeServer() = runBlocking {
		val harness = Harness()
		val bandwagon = BandwagonPowerCoordinator(
			store = harness.bandwagon,
			client = object : BandwagonClient {
				override suspend fun call(
					action: String,
					veid: String,
					apiKey: String,
					timeoutMs: Int,
					extraFields: Map<String, String>
				): BandwagonCallResult {
					throw AssertionError("should not call")
				}
			},
			keys = harness.keys,
			blockedByOtherVendor = { true }
		) { }
		val result = bandwagon.bind(1L, "200", "secret")
		assertTrue(result is BandwagonBindResult.OtherVendor)
	}

	@Test
	fun shutdownWithoutConfirmationDoesNotCallVendor() = runBlocking {
		val harness = Harness()
		harness.links.insert(sampleLink(serverId = 3L, instanceId = "9"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		val result = harness.coordinator.power(3L, CloudconePowerAction.SHUTDOWN, confirmed = false)
		assertTrue(result is CloudconePowerResult.NeedsConfirmation)
		assertTrue(harness.client.powerCalls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun acceptedShutdownClosesBoundSessionAndKeepsLinkOnFailure() = runBlocking {
		val harness = Harness()
		harness.links.insert(sampleLink(serverId = 3L, instanceId = "9"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		harness.client.powerResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		val accepted = harness.coordinator.power(3L, CloudconePowerAction.SHUTDOWN, confirmed = true)
		assertTrue(accepted is CloudconePowerResult.Accepted)
		assertEquals(listOf(3L), harness.closed)
		assertEquals("9", harness.links.getByServerId(3L)?.instanceId)

		harness.client.powerResult = CloudconeCallResult(ok = false, message = "busy", instances = emptyList())
		val failed = harness.coordinator.power(3L, CloudconePowerAction.REBOOT, confirmed = true)
		assertTrue(failed is CloudconePowerResult.Failed)
		assertEquals("9", harness.links.getByServerId(3L)?.instanceId)
	}

	@Test
	fun instanceInfoRequiresBindingAndKeepsLinkOnFailure() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.instanceInfo(1L) is CloudconeInfoResult.NotBound)
		assertTrue(harness.client.informationCalls.isEmpty())

		harness.links.insert(sampleLink(serverId = 1L, instanceId = "9"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		harness.client.informationResult = CloudconeCallResult(
			ok = true,
			message = null,
			instances = emptyList(),
			info = CloudconeInstanceInfo(hostname = "box")
		)
		val known = harness.coordinator.instanceInfo(1L)
		assertTrue(known is CloudconeInfoResult.Known)
		assertEquals("box", (known as CloudconeInfoResult.Known).info.hostname)

		harness.client.informationResult = CloudconeCallResult(ok = false, message = "busy", instances = emptyList())
		val failed = harness.coordinator.instanceInfo(1L)
		assertTrue(failed is CloudconeInfoResult.Failed)
		assertTrue((failed as CloudconeInfoResult.Failed).message.contains("9"))
		assertEquals("9", harness.links.getByServerId(1L)?.instanceId)
	}

	@Test
	fun unboundShutdownDoesNotCallVendor() = runBlocking {
		val harness = Harness()
		harness.client.powerResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		val missing = harness.coordinator.power(9L, CloudconePowerAction.SHUTDOWN, confirmed = true)
		assertTrue(missing is CloudconePowerResult.NoAccount)
		assertTrue(harness.client.powerCalls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	private fun sampleLink(serverId: Long, instanceId: String) = CloudconeLink(
		serverId = serverId,
		instanceId = instanceId,
		encryptedApiKey = "key",
		encryptedHash = "hash"
	)

	private class Harness {
		val links = MemoryCloudconeLinkStore()
		val bandwagon = MemoryBandwagonStore()
		val client = FakeCloudconeClient()
		val keys = MemoryKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = CloudconeCoordinator(
			links = links,
			bandwagonLinks = bandwagon,
			virtualizorLinks = MemoryVirtualizorLinkStore(),
			client = client,
			keys = keys,
			closeSessions = { closed += it }
		)
	}
}

private class FakeCloudconeClient : CloudconeClient {
	var listResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
	var powerResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
	var informationResult: CloudconeCallResult? = null
	val listCalls = mutableListOf<Pair<String, String>>()
	val powerCalls = mutableListOf<String>()
	val informationCalls = mutableListOf<String>()

	override suspend fun list(apiKey: String, hash: String): CloudconeCallResult {
		listCalls += apiKey to hash
		return listResult
	}

	override suspend fun information(apiKey: String, hash: String, instanceId: String): CloudconeCallResult {
		informationCalls += instanceId
		return informationResult ?: CloudconeCallResult(
			ok = true,
			message = null,
			instances = listOf(CloudconeInstance(instanceId, null, null))
		)
	}

	override suspend fun power(
		apiKey: String,
		hash: String,
		instanceId: String,
		action: String
	): CloudconeCallResult {
		powerCalls += action
		return powerResult
	}
}

internal class MemoryKeyStore : BandwagonKeyStore {
	val live = mutableMapOf<String, String>()
	val deleted = mutableListOf<String>()
	private var seq = 0

	override fun encrypt(value: String): String {
		val path = "file-${seq++}"
		live[path] = value
		return path
	}

	override fun decrypt(path: String): String = live.getValue(path)

	override fun delete(path: String) {
		deleted += path
		live.remove(path)
	}
}

internal class MemoryCloudconeLinkStore : CloudconeLinkStore {
	private val links = linkedMapOf<Long, CloudconeLink>()
	private val state = MutableStateFlow<List<CloudconeLink>>(emptyList())
	override suspend fun getByServerId(serverId: Long): CloudconeLink? = links[serverId]
	override suspend fun getByInstanceId(instanceId: String): CloudconeLink? =
		links.values.find { it.instanceId == instanceId }
	override suspend fun all(): List<CloudconeLink> = links.values.toList()
	override suspend fun insert(link: CloudconeLink) {
		links[link.serverId] = link
		state.value = links.values.toList()
	}
	override suspend fun deleteByServerId(serverId: Long) {
		links.remove(serverId)
		state.value = links.values.toList()
	}
	override suspend fun deleteAll() {
		links.clear()
		state.value = emptyList()
	}
	override fun observeAll(): Flow<List<CloudconeLink>> = state
}

internal class MemoryBandwagonStore : BandwagonLinkStore {
	private val links = linkedMapOf<Long, BandwagonLink>()
	override suspend fun getByServerId(serverId: Long): BandwagonLink? = links[serverId]
	override suspend fun getByVeid(veid: String): BandwagonLink? = links.values.find { it.veid == veid }
	override suspend fun upsert(link: BandwagonLink) {
		links[link.serverId] = link
	}
	override suspend fun deleteByServerId(serverId: Long) {
		links.remove(serverId)
	}
	override fun observeBoundServerIds(): Flow<Set<Long>> = MutableStateFlow(emptySet())
}
