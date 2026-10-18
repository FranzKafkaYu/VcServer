package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeAccount
import com.franzkafkayu.vcserver.models.CloudconeLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudconeCoordinatorTest {
	@Test
	fun saveAccountRequiresSuccessfulList() = runBlocking {
		val harness = Harness()
		harness.client.listResult = CloudconeCallResult(ok = false, message = "no", instances = emptyList())
		val failed = harness.coordinator.saveAccount("key", "hash")
		assertTrue(failed is CloudconeAccountResult.Failed)
		assertNull(harness.accounts.get())

		harness.client.listResult = CloudconeCallResult(
			ok = true,
			message = null,
			instances = listOf(CloudconeInstance("9", "1.1.1.1", null))
		)
		val saved = harness.coordinator.saveAccount("key", "hash")
		assertTrue(saved is CloudconeAccountResult.Success)
		assertEquals("key", harness.keys.live[harness.accounts.get()?.encryptedApiKey])
		assertEquals("hash", harness.keys.live[harness.accounts.get()?.encryptedHash])
	}

	@Test
	fun replaceDeletesPreviousKeyFiles() = runBlocking {
		val harness = Harness()
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "old-key", encryptedHash = "old-hash"))
		harness.keys.live["old-key"] = "a"
		harness.keys.live["old-hash"] = "b"
		harness.client.listResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		assertTrue(harness.coordinator.saveAccount("new-key", "new-hash") is CloudconeAccountResult.Success)
		assertTrue(harness.keys.deleted.contains("old-key"))
		assertTrue(harness.keys.deleted.contains("old-hash"))
	}

	@Test
	fun deleteAccountClearsLinksAndSecretsButNotServers() = runBlocking {
		val harness = Harness()
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		harness.links.insert(CloudconeLink(serverId = 4L, instanceId = "9"))
		val servers = mutableListOf(4L)
		harness.coordinator.deleteAccount()
		assertNull(harness.accounts.get())
		assertTrue(harness.links.all().isEmpty())
		assertTrue(harness.keys.deleted.containsAll(listOf("key", "hash")))
		assertEquals(listOf(4L), servers)
	}

	@Test
	fun bindRejectsOtherVendorAndDuplicateInstance() = runBlocking {
		val harness = Harness()
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		harness.bandwagon.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "bw"))
		assertTrue(harness.coordinator.bind(1L, "9") is CloudconeBindResult.OtherVendor)
		assertTrue(harness.links.all().isEmpty())

		harness.bandwagon.deleteByServerId(1L)
		harness.links.insert(CloudconeLink(serverId = 2L, instanceId = "9"))
		assertTrue(harness.coordinator.bind(1L, "9") is CloudconeBindResult.InstanceInUse)
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
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		val result = harness.coordinator.power("9", CloudconePowerAction.SHUTDOWN, confirmed = false)
		assertTrue(result is CloudconePowerResult.NeedsConfirmation)
		assertTrue(harness.client.powerCalls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun acceptedShutdownClosesBoundSessionAndKeepsLinkOnFailure() = runBlocking {
		val harness = Harness()
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		harness.links.insert(CloudconeLink(serverId = 3L, instanceId = "9"))
		harness.client.powerResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		val accepted = harness.coordinator.power("9", CloudconePowerAction.SHUTDOWN, confirmed = true)
		assertTrue(accepted is CloudconePowerResult.Accepted)
		assertEquals(listOf(3L), harness.closed)
		assertEquals("9", harness.links.getByServerId(3L)?.instanceId)

		harness.client.powerResult = CloudconeCallResult(ok = false, message = "busy", instances = emptyList())
		val failed = harness.coordinator.power("9", CloudconePowerAction.REBOOT, confirmed = true)
		assertTrue(failed is CloudconePowerResult.Failed)
		assertEquals("9", harness.links.getByServerId(3L)?.instanceId)
	}

	@Test
	fun unboundShutdownDoesNotCloseSessions() = runBlocking {
		val harness = Harness()
		harness.accounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["hash"] = "digest"
		harness.client.powerResult = CloudconeCallResult(ok = true, message = null, instances = emptyList())
		val accepted = harness.coordinator.power("9", CloudconePowerAction.SHUTDOWN, confirmed = true)
		assertTrue(accepted is CloudconePowerResult.Accepted)
		assertTrue(harness.closed.isEmpty())
	}

	private class Harness {
		val accounts = MemoryCloudconeAccountStore()
		val links = MemoryCloudconeLinkStore()
		val bandwagon = MemoryBandwagonStore()
		val client = FakeCloudconeClient()
		val keys = MemoryKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = CloudconeCoordinator(
			accounts = accounts,
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
	val powerCalls = mutableListOf<String>()

	override suspend fun list(apiKey: String, hash: String): CloudconeCallResult = listResult

	override suspend fun information(apiKey: String, hash: String, instanceId: String): CloudconeCallResult {
		return CloudconeCallResult(
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

internal class MemoryCloudconeAccountStore : CloudconeAccountStore {
	private val state = MutableStateFlow<CloudconeAccount?>(null)
	override suspend fun get(): CloudconeAccount? = state.value
	override suspend fun save(account: CloudconeAccount) {
		state.value = account
	}
	override suspend fun delete() {
		state.value = null
	}
	override fun observe(): Flow<CloudconeAccount?> = state
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
