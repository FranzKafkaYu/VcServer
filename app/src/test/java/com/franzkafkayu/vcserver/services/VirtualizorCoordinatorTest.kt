package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeLink
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
	fun previewFailsWithoutSaving() = runBlocking {
		val harness = Harness()
		harness.client.listResult = VirtualizorCallResult(ok = false, message = "no")
		val failed = harness.coordinator.previewInstances("panel.example", 4083, "key", "pass")
		assertTrue(failed is VirtualizorAccountResult.Failed)
		assertTrue(harness.links.all().isEmpty())
	}

	@Test
	fun bindStoresPanelCredentialsPerServer() = runBlocking {
		val harness = Harness()
		harness.client.listResult = VirtualizorCallResult(
			ok = true,
			message = null,
			instances = listOf(VirtualizorVps("9", "box", "1.1.1.1", "running"))
		)
		assertTrue(harness.coordinator.previewInstances("panel.example", 4083, "key", "pass") is VirtualizorAccountResult.Success)
		val bound = harness.coordinator.bind(1L, "9", "panel.example", 4083, "key", "pass")
		assertTrue(bound is VirtualizorBindResult.Success)
		assertEquals("panel.example", harness.links.getByServerId(1L)?.host)
		assertEquals("key", harness.keys.live[harness.links.getByServerId(1L)?.encryptedApiKey])
	}

	@Test
	fun bindRejectsOtherVendorsAndDuplicatePanelVps() = runBlocking {
		val harness = Harness()
		harness.bandwagon.upsert(BandwagonLink(serverId = 1L, veid = "200", encryptedApiKey = "bw"))
		assertTrue(harness.coordinator.bind(1L, "9", "p", 4083, "key", "pass") is VirtualizorBindResult.OtherVendor)

		harness.bandwagon.deleteByServerId(1L)
		harness.cloudcone.insert(CloudconeLink(serverId = 1L, instanceId = "cc", encryptedApiKey = "k", encryptedHash = "h"))
		assertTrue(harness.coordinator.bind(1L, "9", "p", 4083, "key", "pass") is VirtualizorBindResult.OtherVendor)

		harness.cloudcone.deleteByServerId(1L)
		harness.links.insert(sampleLink(serverId = 2L, vpsid = "9", host = "p"))
		assertTrue(harness.coordinator.bind(1L, "9", "p", 4083, "key", "pass") is VirtualizorBindResult.InstanceInUse)
	}

	@Test
	fun twoPanelsMayShareVpsid() = runBlocking {
		val harness = Harness()
		assertTrue(harness.coordinator.bind(1L, "9", "a.example", 4083, "key-a", "pass-a") is VirtualizorBindResult.Success)
		assertTrue(harness.coordinator.bind(2L, "9", "b.example", 4083, "key-b", "pass-b") is VirtualizorBindResult.Success)
		assertEquals("a.example", harness.links.getByServerId(1L)?.host)
		assertEquals("b.example", harness.links.getByServerId(2L)?.host)
	}

	@Test
	fun cloudconeBindRejectsVirtualizorServer() = runBlocking {
		val harness = Harness()
		harness.vzLinks.insert(sampleLink(serverId = 1L, vpsid = "9", host = "p"))
		val cloudcone = CloudconeCoordinator(
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
		assertTrue(cloudcone.bind(1L, "12", "k", "h") is CloudconeBindResult.OtherVendor)
	}

	@Test
	fun stopWithoutConfirmationDoesNotCallVendor() = runBlocking {
		val harness = Harness()
		harness.links.insert(sampleLink(serverId = 3L, vpsid = "9", host = "p"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		val result = harness.coordinator.power(3L, VirtualizorPowerAction.STOP, confirmed = false)
		assertTrue(result is VirtualizorPowerResult.NeedsConfirmation)
		assertTrue(harness.client.powerCalls.isEmpty())
		assertTrue(harness.closed.isEmpty())
	}

	@Test
	fun acceptedStopUsesLinkHostAndKeepsBindingOnFailure() = runBlocking {
		val harness = Harness()
		harness.links.insert(sampleLink(serverId = 3L, vpsid = "9", host = "panel.example"))
		harness.keys.live["key"] = "secret"
		harness.keys.live["pass"] = "digest"
		harness.client.powerResult = VirtualizorCallResult(ok = true, message = null)
		val accepted = harness.coordinator.power(3L, VirtualizorPowerAction.STOP, confirmed = true)
		assertTrue(accepted is VirtualizorPowerResult.Accepted)
		assertEquals(listOf(3L), harness.closed)
		assertEquals(listOf("panel.example"), harness.client.powerHosts)
		assertEquals("9", harness.links.getByServerId(3L)?.vpsid)

		harness.client.powerResult = VirtualizorCallResult(ok = false, message = "busy")
		val failed = harness.coordinator.power(3L, VirtualizorPowerAction.RESTART, confirmed = true)
		assertTrue(failed is VirtualizorPowerResult.Failed)
		assertEquals("9", harness.links.getByServerId(3L)?.vpsid)
	}

	@Test
	fun bandwidthQueryUsesLinkHost() = runBlocking {
		val harness = Harness()
		harness.links.insert(sampleLink(serverId = 3L, vpsid = "9", host = "panel.example"))
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
		val result = harness.coordinator.bandwidth(3L)
		assertTrue(result is VirtualizorTrafficResult.Known)
		assertEquals(0.79, (result as VirtualizorTrafficResult.Known).bandwidth.usedGb!!, 0.0001)
		assertEquals(listOf("panel.example"), harness.client.bandwidthHosts)
		assertEquals(VirtualizorCoordinator.currentYearMonth(), harness.client.bandwidthShow.single())
	}

	private fun sampleLink(serverId: Long, vpsid: String, host: String) = VirtualizorLink(
		serverId = serverId,
		vpsid = vpsid,
		host = host,
		port = 4083,
		encryptedApiKey = "key",
		encryptedApiPass = "pass"
	)

	private class Harness {
		val links = MemoryVirtualizorLinkStore()
		val vzLinks = links
		val bandwagon = MemoryBandwagonStore()
		val cloudcone = MemoryCloudconeLinkStore()
		val client = FakeVirtualizorClient()
		val keys = MemoryKeyStore()
		val closed = mutableListOf<Long>()
		val coordinator = VirtualizorCoordinator(
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
	val powerHosts = mutableListOf<String>()
	var bandwidthResult = VirtualizorCallResult(ok = true, message = null)
	val bandwidthHosts = mutableListOf<String>()
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
		powerHosts += host
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
		bandwidthHosts += host
		bandwidthShow += show
		return bandwidthResult
	}
}

internal class MemoryVirtualizorLinkStore : VirtualizorLinkStore {
	private val links = linkedMapOf<Long, VirtualizorLink>()
	private val state = MutableStateFlow<List<VirtualizorLink>>(emptyList())
	override suspend fun getByServerId(serverId: Long): VirtualizorLink? = links[serverId]
	override suspend fun getByPanelVps(host: String, port: Int, vpsid: String): VirtualizorLink? =
		links.values.find { it.host == host && it.port == port && it.vpsid == vpsid }
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
