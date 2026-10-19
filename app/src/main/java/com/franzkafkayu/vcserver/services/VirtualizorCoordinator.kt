package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.data.dao.VirtualizorLinkDao
import com.franzkafkayu.vcserver.models.VirtualizorLink
import kotlinx.coroutines.flow.Flow

interface VirtualizorLinkStore {
	suspend fun getByServerId(serverId: Long): VirtualizorLink?
	suspend fun getByPanelVps(host: String, port: Int, vpsid: String): VirtualizorLink?
	suspend fun all(): List<VirtualizorLink>
	suspend fun insert(link: VirtualizorLink)
	suspend fun deleteByServerId(serverId: Long)
	suspend fun deleteAll()
	fun observeAll(): Flow<List<VirtualizorLink>>
}

class RoomVirtualizorLinkStore(
	private val dao: VirtualizorLinkDao
) : VirtualizorLinkStore {
	override suspend fun getByServerId(serverId: Long): VirtualizorLink? = dao.getByServerId(serverId)
	override suspend fun getByPanelVps(host: String, port: Int, vpsid: String): VirtualizorLink? =
		dao.getByPanelVps(host, port, vpsid)
	override suspend fun all(): List<VirtualizorLink> = dao.all()
	override suspend fun insert(link: VirtualizorLink) {
		dao.insert(link.copy(id = 0))
	}
	override suspend fun deleteByServerId(serverId: Long) = dao.deleteByServerId(serverId)
	override suspend fun deleteAll() = dao.deleteAll()
	override fun observeAll(): Flow<List<VirtualizorLink>> = dao.observeAll()
}

enum class VirtualizorPowerAction { START, STOP, RESTART }

sealed class VirtualizorAccountResult {
	data object Success : VirtualizorAccountResult()
	data class NeedsCertificatePin(val sha256: String) : VirtualizorAccountResult()
	data class Failed(val message: String) : VirtualizorAccountResult()
}

sealed class VirtualizorBindResult {
	data object Success : VirtualizorBindResult()
	data object OtherVendor : VirtualizorBindResult()
	data object InstanceInUse : VirtualizorBindResult()
	data class Failed(val message: String) : VirtualizorBindResult()
}

sealed class VirtualizorPowerResult {
	data object Accepted : VirtualizorPowerResult()
	data object NeedsConfirmation : VirtualizorPowerResult()
	data object NoAccount : VirtualizorPowerResult()
	data class Failed(val message: String) : VirtualizorPowerResult()
}

sealed class VirtualizorStatusResult {
	data class Known(val powerStatus: String) : VirtualizorStatusResult()
	data object Unknown : VirtualizorStatusResult()
	data class Failed(val message: String) : VirtualizorStatusResult()
}

sealed class VirtualizorTrafficResult {
	data class Known(val bandwidth: VirtualizorBandwidth) : VirtualizorTrafficResult()
	data object Unknown : VirtualizorTrafficResult()
	data object NoAccount : VirtualizorTrafficResult()
	data class Failed(val message: String) : VirtualizorTrafficResult()
}

class VirtualizorCoordinator(
	private val links: VirtualizorLinkStore,
	private val bandwagonLinks: BandwagonLinkStore,
	private val cloudconeLinks: CloudconeLinkStore,
	private val greencloudLinks: GreencloudLinkStore,
	private val client: VirtualizorClient,
	private val keys: BandwagonKeyStore,
	private val closeSessions: (Long) -> Unit
) {
	fun observeLinks(): Flow<List<VirtualizorLink>> = links.observeAll()

	suspend fun listInstances(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		confirmedPin: String? = null
	): VirtualizorCallResult {
		val trimmedHost = host.trim()
		val trimmedKey = apiKey.trim()
		val trimmedPass = apiPass.trim()
		if (trimmedHost.isEmpty() || trimmedKey.isEmpty() || trimmedPass.isEmpty()) {
			return VirtualizorCallResult(ok = false, message = "host, API key and API pass are required")
		}
		if (port !in 1..65535) {
			return VirtualizorCallResult(ok = false, message = "invalid port")
		}
		return try {
			client.list(trimmedHost, port, trimmedKey, trimmedPass, confirmedPin)
		} catch (e: Exception) {
			VirtualizorCallResult(ok = false, message = e.message ?: "request failed")
		}
	}

	suspend fun previewInstances(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		confirmedPin: String? = null
	): VirtualizorAccountResult {
		val listed = listInstances(host, port, apiKey, apiPass, confirmedPin)
		if (!listed.ok && !listed.certPinSha256.isNullOrBlank() && confirmedPin == null) {
			return VirtualizorAccountResult.NeedsCertificatePin(listed.certPinSha256)
		}
		if (!listed.ok) {
			return VirtualizorAccountResult.Failed(listed.message ?: "request failed")
		}
		return VirtualizorAccountResult.Success
	}

	suspend fun bind(
		serverId: Long,
		vpsid: String,
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		certPinSha256: String? = null
	): VirtualizorBindResult {
		if (bandwagonLinks.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		if (cloudconeLinks.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		if (greencloudLinks.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		if (links.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		val trimmedHost = host.trim()
		val occupied = links.getByPanelVps(trimmedHost, port, vpsid)
		if (occupied != null && occupied.serverId != serverId) return VirtualizorBindResult.InstanceInUse
		val encryptedKey = keys.encrypt(apiKey.trim())
		val encryptedPass = keys.encrypt(apiPass.trim())
		return try {
			links.insert(
				VirtualizorLink(
					serverId = serverId,
					vpsid = vpsid,
					host = trimmedHost,
					port = port,
					encryptedApiKey = encryptedKey,
					encryptedApiPass = encryptedPass,
					certPinSha256 = certPinSha256
				)
			)
			VirtualizorBindResult.Success
		} catch (e: Exception) {
			keys.delete(encryptedKey)
			keys.delete(encryptedPass)
			VirtualizorBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		onServerDeleted(serverId)
	}

	suspend fun onServerDeleted(serverId: Long) {
		val previous = links.getByServerId(serverId) ?: return
		links.deleteByServerId(serverId)
		keys.delete(previous.encryptedApiKey)
		keys.delete(previous.encryptedApiPass)
	}

	suspend fun power(
		serverId: Long,
		action: VirtualizorPowerAction,
		confirmed: Boolean
	): VirtualizorPowerResult {
		if (action != VirtualizorPowerAction.START && !confirmed) {
			return VirtualizorPowerResult.NeedsConfirmation
		}
		val session = session(serverId) ?: return VirtualizorPowerResult.NoAccount
		val result = try {
			client.power(
				session.host,
				session.port,
				session.apiKey,
				session.apiPass,
				session.vpsid,
				action.endpoint(),
				session.pin
			)
		} catch (e: Exception) {
			return VirtualizorPowerResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return VirtualizorPowerResult.Failed(result.message ?: "request failed")
		if (action != VirtualizorPowerAction.START) {
			closeSessions(serverId)
		}
		return VirtualizorPowerResult.Accepted
	}

	suspend fun status(serverId: Long): VirtualizorStatusResult {
		val session = session(serverId) ?: return VirtualizorStatusResult.Failed("not bound")
		val listed = try {
			client.list(session.host, session.port, session.apiKey, session.apiPass, session.pin)
		} catch (e: Exception) {
			return VirtualizorStatusResult.Failed(e.message ?: "request failed")
		}
		if (!listed.ok) return VirtualizorStatusResult.Failed(listed.message ?: "request failed")
		val power = listed.instances.firstOrNull { it.id == session.vpsid }?.powerStatus
		if (power.isNullOrBlank()) return VirtualizorStatusResult.Unknown
		return VirtualizorStatusResult.Known(power)
	}

	suspend fun bandwidth(serverId: Long, show: String? = currentYearMonth()): VirtualizorTrafficResult {
		val session = session(serverId) ?: return VirtualizorTrafficResult.NoAccount
		val result = try {
			client.bandwidth(
				session.host,
				session.port,
				session.apiKey,
				session.apiPass,
				session.vpsid,
				show,
				session.pin
			)
		} catch (e: Exception) {
			return VirtualizorTrafficResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return VirtualizorTrafficResult.Failed(result.message ?: "request failed")
		val bandwidth = result.bandwidth ?: return VirtualizorTrafficResult.Unknown
		if (bandwidth.usedGb == null) return VirtualizorTrafficResult.Unknown
		return VirtualizorTrafficResult.Known(bandwidth)
	}

	companion object {
		fun currentYearMonth(): String {
			val calendar = java.util.Calendar.getInstance()
			val year = calendar.get(java.util.Calendar.YEAR)
			val month = calendar.get(java.util.Calendar.MONTH) + 1
			return "%04d%02d".format(year, month)
		}
	}

	private suspend fun session(serverId: Long): PanelSession? {
		val link = links.getByServerId(serverId) ?: return null
		return PanelSession(
			host = link.host,
			port = link.port,
			apiKey = keys.decrypt(link.encryptedApiKey),
			apiPass = keys.decrypt(link.encryptedApiPass),
			vpsid = link.vpsid,
			pin = link.certPinSha256
		)
	}

	private fun VirtualizorPowerAction.endpoint(): String {
		return when (this) {
			VirtualizorPowerAction.START -> HttpVirtualizorClient.ACTION_START
			VirtualizorPowerAction.STOP -> HttpVirtualizorClient.ACTION_STOP
			VirtualizorPowerAction.RESTART -> HttpVirtualizorClient.ACTION_RESTART
		}
	}

	private data class PanelSession(
		val host: String,
		val port: Int,
		val apiKey: String,
		val apiPass: String,
		val vpsid: String,
		val pin: String?
	)
}
