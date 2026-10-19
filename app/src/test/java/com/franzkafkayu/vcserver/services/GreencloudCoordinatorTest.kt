package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeLink
import com.franzkafkayu.vcserver.models.GreencloudLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreencloudCoordinatorTest {
	@Test
	fun bindRejectsOtherVendorAndDuplicateInstance() = runBlocking {
		val harness = Harness()
		harness.bandwagon.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "bw"))
		assertTrue(harness.coordinator.bind(1L, "9", "token") is GreencloudBindResult.OtherVendor)

		harness.bandwagon.deleteByServerId(1L)
		harness.links.insert(sampleLink(serverId = 2L, instanceId = "9"))
		assertTrue(harness.coordinator.bind(1L, "9", "token") is GreencloudBindResult.InstanceInUse)
	}

	@Test
	fun bindStoresPerServerCredentials() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.bind(1L, "9", "token-a") is GreencloudBindResult.Success)
		assertTrue(harness.coordinator.bind(2L, "10", "token-b") is GreencloudBindResult.Success)
		assertEquals("token-a", harness.keys.live[harness.links.getByServerId(1L)?.encryptedApiToken])
		assertEquals("token-b", harness.keys.live[harness.links.getByServerId(2L)?.encryptedApiToken])
	}

	@Test
	fun instanceInfoUsesBoundToken() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.bind(1L, "6b32", "token-a") is GreencloudBindResult.Success)
		val result = harness.coordinator.instanceInfo(1L)
		assertTrue(result is GreencloudInfoResult.Known)
		assertEquals("Test VM", (result as GreencloudInfoResult.Known).info.name)
	}

	@Test
	fun resetPasswordNeedsConfirmationThenReturnsPassword() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.bind(1L, "6b32", "token-a") is GreencloudBindResult.Success)
		assertTrue(harness.coordinator.resetPassword(1L, confirmed = false) is GreencloudResetResult.NeedsConfirmation)
		assertTrue(harness.closed.isEmpty())
		val accepted = harness.coordinator.resetPassword(1L, confirmed = true)
		assertTrue(accepted is GreencloudResetResult.Accepted)
		assertEquals("new-pass", (accepted as GreencloudResetResult.Accepted).password)
		assertEquals(listOf(1L), harness.closed)
	}

	@Test
	fun cloudconeOccupiedIsOtherVendor() = runBlocking {
		val harness = Harness()
		harness.cloudcone.insert(
			CloudconeLink(serverId = 1L, instanceId = "cc", encryptedApiKey = "k", encryptedHash = "h")
		)
		assertTrue(harness.coordinator.bind(1L, "9", "token") is GreencloudBindResult.OtherVendor)
	}

	private fun sampleLink(serverId: Long, instanceId: String) = GreencloudLink(
		serverId = serverId,
		instanceId = instanceId,
		encryptedApiToken = "token"
	)

	private class Harness {
		val links = MemoryGreencloudLinkStore()
		val bandwagon = MemoryBandwagonStore()
		val cloudcone = MemoryCloudconeLinkStore()
		val client = FakeGreencloudClient()
		val keys = MemoryKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = GreencloudCoordinator(
			links = links,
			bandwagonLinks = bandwagon,
			cloudconeLinks = cloudcone,
			virtualizorLinks = MemoryVirtualizorLinkStore(),
			client = client,
			keys = keys,
			closeSessions = { closed += it }
		)
	}
}

private class FakeGreencloudClient : GreencloudClient {
	var listResult = GreencloudCallResult(ok = true, message = null, instances = emptyList())
	override suspend fun list(apiToken: String) = listResult
	override suspend fun power(
		apiToken: String,
		instanceId: String,
		action: GreencloudPowerAction
	) = GreencloudCallResult(ok = true, message = null, instances = emptyList())
	override suspend fun status(apiToken: String, instanceId: String) =
		GreencloudCallResult(ok = true, message = null, instances = emptyList(), powerStatus = "running")
	var detailsResult = GreencloudCallResult(
		ok = true,
		message = null,
		instances = emptyList(),
		info = GreencloudInstanceInfo(name = "Test VM", cpu = "2 Core")
	)
	var resetResult = GreencloudCallResult(
		ok = true,
		message = null,
		instances = emptyList(),
		expectedPassword = "new-pass"
	)
	override suspend fun details(apiToken: String, instanceId: String) = detailsResult
	override suspend fun resetPassword(apiToken: String, instanceId: String) = resetResult
}

internal class MemoryGreencloudLinkStore : GreencloudLinkStore {
	private val links = linkedMapOf<Long, GreencloudLink>()
	private val state = MutableStateFlow<List<GreencloudLink>>(emptyList())
	override suspend fun getByServerId(serverId: Long): GreencloudLink? = links[serverId]
	override suspend fun getByInstanceId(instanceId: String): GreencloudLink? =
		links.values.find { it.instanceId == instanceId }
	override suspend fun all(): List<GreencloudLink> = links.values.toList()
	override suspend fun insert(link: GreencloudLink) {
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
	override fun observeAll(): Flow<List<GreencloudLink>> = state
}
