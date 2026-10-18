package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeAccount
import com.franzkafkayu.vcserver.models.CloudconeLink
import com.franzkafkayu.vcserver.models.VirtualizorAccount
import com.franzkafkayu.vcserver.models.VirtualizorLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualizorCoordinatorTest {
	@Test
	fun saveAccountRequiresSuccessfulList() = runBlocking {
		val harness = Harness()
		harness.client.listResult = VirtualizorCallResult(ok = false, message = "no")
		val failed = harness.coordinator.saveAccount("panel.example", 4083, "key", "pass")
		assertTrue(failed is VirtualizorAccountResult.Failed)
		assertNull(harness.accounts.get())

		harness.client.listResult = VirtualizorCallResult(
			ok = true,
			message = null,
			instances = listOf(VirtualizorVps("9", "box", "1.1.1.1", "running"))
		)
		val saved = harness.coordinator.saveAccount("panel.example", 4083, "key", "pass")
		assertTrue(saved is VirtualizorAccountResult.Success)
		assertEquals("key", harness.keys.live[harness.accounts.get()?.encryptedApiKey])
		assertEquals("pass", harness.keys.live[harness.accounts.get()?.encryptedApiPass])
	}

	@Test
	fun replaceClearsLinksAndPreviousSecrets() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(
				host = "old.example",
				port = 4083,
				encryptedApiKey = "old-key",
				encryptedApiPass = "old-pass"
			)
		)
		harness.keys.live["old-key"] = "a"
		harness.keys.live["old-pass"] = "b"
		harness.links.insert(VirtualizorLink(serverId = 4L, vpsid = "9"))
		harness.client.listResult = VirtualizorCallResult(ok = true, message = null)
		assertTrue(harness.coordinator.saveAccount("new.example", 4083, "new-key", "new-pass") is VirtualizorAccountResult.Success)
		assertTrue(harness.links.all().isEmpty())
		assertTrue(harness.keys.deleted.contains("old-key"))
		assertTrue(harness.keys.deleted.contains("old-pass"))
	}

	@Test
	fun deleteAccountClearsLinksAndSecretsButNotServers() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(host = "p", port = 4083, encryptedApiKey = "key", encryptedApiPass = "pass")
		)
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		harness.links.insert(VirtualizorLink(serverId = 4L, vpsid = "9"))
		val servers = mutableListOf(4L)
		harness.coordinator.deleteAccount()
		assertNull(harness.accounts.get())
		assertTrue(harness.links.all().isEmpty())
		assertTrue(harness.keys.deleted.containsAll(listOf("key", "pass")))
		assertEquals(listOf(4L), servers)
	}

	@Test
	fun bindRejectsOtherVendorsAndDuplicateVps() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(host = "p", port = 4083, encryptedApiKey = "key", encryptedApiPass = "pass")
		)
		harness.bandwagon.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "bw"))
		assertTrue(harness.coordinator.bind(1L, "9") is VirtualizorBindResult.OtherVendor)

		harness.bandwagon.deleteByServerId(1L)
		harness.cloudcone.insert(CloudconeLink(serverId = 1L, instanceId = "cc"))
		assertTrue(harness.coordinator.bind(1L, "9") is VirtualizorBindResult.OtherVendor)

		harness.cloudcone.deleteByServerId(1L)
		harness.links.insert(VirtualizorLink(serverId = 2L, vpsid = "9"))
		assertTrue(harness.coordinator.bind(1L, "9") is VirtualizorBindResult.InstanceInUse)
	}

	@Test
	fun cloudconeBindRejectsVirtualizorServer() = runBlocking {
		val harness = Harness()
		harness.vzLinks.insert(VirtualizorLink(serverId = 1L, vpsid = "9"))
		harness.ccAccounts.save(CloudconeAccount(encryptedApiKey = "key", encryptedHash = "hash"))
		val cloudcone = CloudconeCoordinator(
			accounts = harness.ccAccounts,
			links = harness.cloudcone,
			bandwagonLinks = harness.bandwagon,
			virtualizorLinks = harness.vzLinks,
			client = object : CloudconeClient {
				override suspend fun list(apiKey: String, hash: String) = throw AssertionError()
				override suspend fun information(apiKey: String, hash: String, instanceId: String) = throw AssertionError()
				override suspend fun power(apiKey: String, hash: String, instanceId: String, action: String) = throw AssertionError()
			},
			keys = harness.keys
		) { }
		assertTrue(cloudcone.bind(1L, "12") is CloudconeBindResult.OtherVendor)
	}

	@Test
	fun stopWithoutConfirmationDoesNotCallVendor() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(host = "p", port = 4083, encryptedApiKey = "key", encryptedApiPass = "pass")
		)
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		val result = harness.coordinator.power("9", VirtualizorPowerAction.STOP, confirmed = false)
		assertTrue(result is VirtualizorPowerResult.NeedsConfirmation)
		assertTrue(harness.client.powerCalls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun acceptedStopClosesBoundSessionAndKeepsLinkOnFailure() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(host = "p", port = 4083, encryptedApiKey = "key", encryptedApiPass = "pass")
		)
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		harness.links.insert(VirtualizorLink(serverId = 3L, vpsid = "9"))
		harness.client.powerResult = VirtualizorCallResult(ok = true, message = null)
		val accepted = harness.coordinator.power("9", VirtualizorPowerAction.STOP, confirmed = true)
		assertTrue(accepted is VirtualizorPowerResult.Accepted)
		assertEquals(listOf(3L), harness.closed)
		assertEquals("stop", harness.client.powerCalls.single())
		assertEquals("9", harness.links.getByServerId(3L)?.vpsid)

		harness.client.powerResult = VirtualizorCallResult(ok = false, message = "busy")
		val failed = harness.coordinator.power("9", VirtualizorPowerAction.RESTART, confirmed = true)
		assertTrue(failed is VirtualizorPowerResult.Failed)
		assertEquals("9", harness.links.getByServerId(3L)?.vpsid)
	}

	@Test
	fun bandwidthQueryUsesCurrentMonthAndReturnsUsage() = runBlocking {
		val harness = Harness()
		harness.accounts.save(
			VirtualizorAccount(host = "p", port = 4083, encryptedApiKey = "key", encryptedApiPass = "pass")
		)
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		harness.client.bandwidthResult = VirtualizorCallResult(
			ok = true,
			message = null,
			bandwidth = VirtualizorBandwidth(
				usedGb = 0.79,
				limitGb = 0.0,
				percent = 0.0,
				inGb = null,
				outGb = null,
				unlimited = true
			)
		)
		val result = harness.coordinator.bandwidth("9")
		assertTrue(result is VirtualizorTrafficResult.Known)
		assertEquals(0.79, (result as VirtualizorTrafficResult.Known).bandwidth.usedGb!!, 0.0001)
		assertEquals(listOf("9"), harness.client.bandwidthVps)
		assertEquals(VirtualizorCoordinator.currentYearMonth(), harness.client.bandwidthShow.single())
	}

	private class Harness {
		val accounts = MemoryVirtualizorAccountStore()
		val links = MemoryVirtualizorLinkStore()
		val vzLinks = links
		val bandwagon = MemoryBandwagonStore()
		val cloudcone = MemoryCloudconeLinkStore()
		val ccAccounts = MemoryCloudconeAccountStore()
		val client = FakeVirtualizorClient()
		val keys = MemoryKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = VirtualizorCoordinator(
			accounts = accounts,
			links = links,
			bandwagonLinks = bandwagon,
			cloudconeLinks = cloudcone,
			client = client,
			keys = keys,
			closeSessions = { closed += it }
		)
	}
}

private class FakeVirtualizorClient : VirtualizorClient {
	var listResult = VirtualizorCallResult(ok = true, message = null)
	var powerResult = VirtualizorCallResult(ok = true, message = null)
	val powerCalls = mutableListOf<String>()
	var bandwidthResult = VirtualizorCallResult(ok = true, message = null)
	val bandwidthVps = mutableListOf<String>()
	val bandwidthShow = mutableListOf<String?>()

	override suspend fun list(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		certPinSha256: String?
	): VirtualizorCallResult = listResult

	override suspend fun power(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		action: String,
		certPinSha256: String?
	): VirtualizorCallResult {
		powerCalls += action
		return powerResult
	}

	override suspend fun bandwidth(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		vpsid: String,
		show: String?,
		certPinSha256: String?
	): VirtualizorCallResult {
		bandwidthVps += vpsid
		bandwidthShow += show
		return bandwidthResult
	}
}

internal class MemoryVirtualizorAccountStore : VirtualizorAccountStore {
	private val state = MutableStateFlow<VirtualizorAccount?>(null)
	override suspend fun get(): VirtualizorAccount? = state.value
	override suspend fun save(account: VirtualizorAccount) {
		state.value = account
	}
	override suspend fun delete() {
		state.value = null
	}
	override fun observe(): Flow<VirtualizorAccount?> = state
}

internal class MemoryVirtualizorLinkStore : VirtualizorLinkStore {
	private val links = linkedMapOf<Long, VirtualizorLink>()
	private val state = MutableStateFlow<List<VirtualizorLink>>(emptyList())
	override suspend fun getByServerId(serverId: Long): VirtualizorLink? = links[serverId]
	override suspend fun getByVpsid(vpsid: String): VirtualizorLink? = links.values.find { it.vpsid == vpsid }
	override suspend fun all(): List<VirtualizorLink> = links.values.toList()
	override suspend fun insert(link: VirtualizorLink) {
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
	override fun observeAll(): Flow<List<VirtualizorLink>> = state
}
