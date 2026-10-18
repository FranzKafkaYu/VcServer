package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.data.dao.VirtualizorAccountDao
import com.franzkafkayu.vcserver.data.dao.VirtualizorLinkDao
import com.franzkafkayu.vcserver.models.VirtualizorAccount
import com.franzkafkayu.vcserver.models.VirtualizorLink
import kotlinx.coroutines.flow.Flow

interface VirtualizorAccountStore {
	suspend fun get(): VirtualizorAccount?
	suspend fun save(account: VirtualizorAccount)
	suspend fun delete()
	fun observe(): Flow<VirtualizorAccount?>
}

class RoomVirtualizorAccountStore(
	private val dao: VirtualizorAccountDao
) : VirtualizorAccountStore {
	override suspend fun get(): VirtualizorAccount? = dao.get()
	override suspend fun save(account: VirtualizorAccount) {
		dao.upsert(account.copy(id = VirtualizorAccount.SINGLE_ID))
	}
	override suspend fun delete() = dao.delete()
	override fun observe(): Flow<VirtualizorAccount?> = dao.observe()
}

interface VirtualizorLinkStore {
	suspend fun getByServerId(serverId: Long): VirtualizorLink?
	suspend fun getByVpsid(vpsid: String): VirtualizorLink?
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
	override suspend fun getByVpsid(vpsid: String): VirtualizorLink? = dao.getByVpsid(vpsid)
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
	data object NoAccount : VirtualizorBindResult()
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
	private val accounts: VirtualizorAccountStore,
	private val links: VirtualizorLinkStore,
	private val bandwagonLinks: BandwagonLinkStore,
	private val cloudconeLinks: CloudconeLinkStore,
	private val client: VirtualizorClient,
	private val keys: BandwagonKeyStore,
	private val closeSessions: (Long) -> Unit
) {
	fun observeAccount(): Flow<VirtualizorAccount?> = accounts.observe()
	fun observeLinks(): Flow<List<VirtualizorLink>> = links.observeAll()

	suspend fun saveAccount(
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		confirmedPin: String? = null
	): VirtualizorAccountResult {
		val trimmedHost = host.trim()
		val trimmedKey = apiKey.trim()
		val trimmedPass = apiPass.trim()
		if (trimmedHost.isEmpty() || trimmedKey.isEmpty() || trimmedPass.isEmpty()) {
			return VirtualizorAccountResult.Failed("host, API key and API pass are required")
		}
		if (port !in 1..65535) {
			return VirtualizorAccountResult.Failed("invalid port")
		}
		val listed = try {
			client.list(trimmedHost, port, trimmedKey, trimmedPass, confirmedPin)
		} catch (e: Exception) {
			return VirtualizorAccountResult.Failed(e.message ?: "request failed")
		}
		if (!listed.ok && !listed.certPinSha256.isNullOrBlank() && confirmedPin == null) {
			return VirtualizorAccountResult.NeedsCertificatePin(listed.certPinSha256)
		}
		if (!listed.ok) {
			return VirtualizorAccountResult.Failed(listed.message ?: "request failed")
		}
		val encryptedKey = keys.encrypt(trimmedKey)
		val encryptedPass = keys.encrypt(trimmedPass)
		val previous = accounts.get()
		return try {
			if (previous != null) {
				links.deleteAll()
			}
			accounts.save(
				VirtualizorAccount(
					host = trimmedHost,
					port = port,
					encryptedApiKey = encryptedKey,
					encryptedApiPass = encryptedPass,
					certPinSha256 = confirmedPin ?: previous
						?.takeIf { it.host == trimmedHost && it.port == port }
						?.certPinSha256
				)
			)
			if (previous != null) {
				if (previous.encryptedApiKey != encryptedKey) keys.delete(previous.encryptedApiKey)
				if (previous.encryptedApiPass != encryptedPass) keys.delete(previous.encryptedApiPass)
			}
			VirtualizorAccountResult.Success
		} catch (e: Exception) {
			keys.delete(encryptedKey)
			keys.delete(encryptedPass)
			VirtualizorAccountResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun deleteAccount() {
		val previous = accounts.get()
		links.deleteAll()
		accounts.delete()
		previous?.let {
			keys.delete(it.encryptedApiKey)
			keys.delete(it.encryptedApiPass)
		}
	}

	suspend fun onServerDeleted(serverId: Long) {
		links.deleteByServerId(serverId)
	}

	suspend fun bind(serverId: Long, vpsid: String): VirtualizorBindResult {
		if (bandwagonLinks.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		if (cloudconeLinks.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		val occupied = links.getByVpsid(vpsid)
		if (occupied != null && occupied.serverId != serverId) return VirtualizorBindResult.InstanceInUse
		if (accounts.get() == null) return VirtualizorBindResult.NoAccount
		if (links.getByServerId(serverId) != null) return VirtualizorBindResult.OtherVendor
		return try {
			links.insert(VirtualizorLink(serverId = serverId, vpsid = vpsid))
			VirtualizorBindResult.Success
		} catch (e: Exception) {
			VirtualizorBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		links.deleteByServerId(serverId)
	}

	suspend fun listInstances(): VirtualizorCallResult {
		val session = session() ?: return VirtualizorCallResult(ok = false, message = "no account")
		return try {
			client.list(session.host, session.port, session.apiKey, session.apiPass, session.pin)
		} catch (e: Exception) {
			VirtualizorCallResult(ok = false, message = e.message ?: "request failed")
		}
	}

	suspend fun power(
		vpsid: String,
		action: VirtualizorPowerAction,
		confirmed: Boolean
	): VirtualizorPowerResult {
		if (action != VirtualizorPowerAction.START && !confirmed) {
			return VirtualizorPowerResult.NeedsConfirmation
		}
		val session = session() ?: return VirtualizorPowerResult.NoAccount
		val result = try {
			client.power(
				session.host,
				session.port,
				session.apiKey,
				session.apiPass,
				vpsid,
				action.endpoint(),
				session.pin
			)
		} catch (e: Exception) {
			return VirtualizorPowerResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return VirtualizorPowerResult.Failed(result.message ?: "request failed")
		if (action != VirtualizorPowerAction.START) {
			links.getByVpsid(vpsid)?.let { closeSessions(it.serverId) }
		}
		return VirtualizorPowerResult.Accepted
	}

	suspend fun status(vpsid: String): VirtualizorStatusResult {
		val listed = listInstances()
		if (!listed.ok) return VirtualizorStatusResult.Failed(listed.message ?: "request failed")
		val power = listed.instances.firstOrNull { it.id == vpsid }?.powerStatus
		if (power.isNullOrBlank()) return VirtualizorStatusResult.Unknown
		return VirtualizorStatusResult.Known(power)
	}

	suspend fun bandwidth(vpsid: String, show: String? = currentYearMonth()): VirtualizorTrafficResult {
		val session = session() ?: return VirtualizorTrafficResult.NoAccount
		val result = try {
			client.bandwidth(
				session.host,
				session.port,
				session.apiKey,
				session.apiPass,
				vpsid,
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

	private suspend fun session(): PanelSession? {
		val account = accounts.get() ?: return null
		return PanelSession(
			host = account.host,
			port = account.port,
			apiKey = keys.decrypt(account.encryptedApiKey),
			apiPass = keys.decrypt(account.encryptedApiPass),
			pin = account.certPinSha256
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
		val pin: String?
	)
}
