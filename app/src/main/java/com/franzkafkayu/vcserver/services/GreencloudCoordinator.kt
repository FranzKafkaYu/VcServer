package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.data.dao.GreencloudLinkDao
import com.franzkafkayu.vcserver.models.GreencloudLink
import kotlinx.coroutines.flow.Flow

interface GreencloudLinkStore {
	suspend fun getByServerId(serverId: Long): GreencloudLink?
	suspend fun getByInstanceId(instanceId: String): GreencloudLink?
	suspend fun all(): List<GreencloudLink>
	suspend fun insert(link: GreencloudLink)
	suspend fun deleteByServerId(serverId: Long)
	suspend fun deleteAll()
	fun observeAll(): Flow<List<GreencloudLink>>
}

class RoomGreencloudLinkStore(
	private val dao: GreencloudLinkDao
) : GreencloudLinkStore {
	override suspend fun getByServerId(serverId: Long): GreencloudLink? = dao.getByServerId(serverId)
	override suspend fun getByInstanceId(instanceId: String): GreencloudLink? = dao.getByInstanceId(instanceId)
	override suspend fun all(): List<GreencloudLink> = dao.all()
	override suspend fun insert(link: GreencloudLink) {
		dao.insert(link.copy(id = 0))
	}
	override suspend fun deleteByServerId(serverId: Long) = dao.deleteByServerId(serverId)
	override suspend fun deleteAll() = dao.deleteAll()
	override fun observeAll(): Flow<List<GreencloudLink>> = dao.observeAll()
}

enum class GreencloudPowerAction { START, STOP, RESTART }

sealed class GreencloudBindResult {
	data object Success : GreencloudBindResult()
	data object OtherVendor : GreencloudBindResult()
	data object InstanceInUse : GreencloudBindResult()
	data class Failed(val message: String) : GreencloudBindResult()
}

sealed class GreencloudPowerResult {
	data object Accepted : GreencloudPowerResult()
	data object NeedsConfirmation : GreencloudPowerResult()
	data object NoAccount : GreencloudPowerResult()
	data class Failed(val message: String) : GreencloudPowerResult()
}

sealed class GreencloudStatusResult {
	data class Known(val powerStatus: String) : GreencloudStatusResult()
	data object Unknown : GreencloudStatusResult()
	data class Failed(val message: String) : GreencloudStatusResult()
}

sealed class GreencloudInfoResult {
	data class Known(val info: GreencloudInstanceInfo) : GreencloudInfoResult()
	data object NotBound : GreencloudInfoResult()
	data class Failed(val message: String) : GreencloudInfoResult()
}

sealed class GreencloudResetResult {
	data class Accepted(val password: String) : GreencloudResetResult()
	data object NeedsConfirmation : GreencloudResetResult()
	data object NotBound : GreencloudResetResult()
	data class Failed(val message: String) : GreencloudResetResult()
}

class GreencloudCoordinator(
	private val links: GreencloudLinkStore,
	private val bandwagonLinks: BandwagonLinkStore,
	private val cloudconeLinks: CloudconeLinkStore,
	private val virtualizorLinks: VirtualizorLinkStore,
	private val client: GreencloudClient,
	private val keys: BandwagonKeyStore,
	private val closeSessions: (Long) -> Unit
) {
	fun observeLinks(): Flow<List<GreencloudLink>> = links.observeAll()

	suspend fun listInstances(apiToken: String): GreencloudCallResult {
		val trimmed = apiToken.trim()
		if (trimmed.isEmpty()) {
			return GreencloudCallResult(ok = false, message = "API Token is required", instances = emptyList())
		}
		return try {
			client.list(trimmed)
		} catch (e: Exception) {
			GreencloudCallResult(ok = false, message = e.message ?: "request failed", instances = emptyList())
		}
	}

	suspend fun bind(
		serverId: Long,
		instanceId: String,
		apiToken: String
	): GreencloudBindResult {
		if (bandwagonLinks.getByServerId(serverId) != null) return GreencloudBindResult.OtherVendor
		if (cloudconeLinks.getByServerId(serverId) != null) return GreencloudBindResult.OtherVendor
		if (virtualizorLinks.getByServerId(serverId) != null) return GreencloudBindResult.OtherVendor
		if (links.getByServerId(serverId) != null) return GreencloudBindResult.OtherVendor
		val occupied = links.getByInstanceId(instanceId)
		if (occupied != null && occupied.serverId != serverId) return GreencloudBindResult.InstanceInUse
		val encryptedToken = keys.encrypt(apiToken.trim())
		return try {
			links.insert(
				GreencloudLink(
					serverId = serverId,
					instanceId = instanceId,
					encryptedApiToken = encryptedToken
				)
			)
			GreencloudBindResult.Success
		} catch (e: Exception) {
			keys.delete(encryptedToken)
			GreencloudBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		onServerDeleted(serverId)
	}

	suspend fun onServerDeleted(serverId: Long) {
		val previous = links.getByServerId(serverId) ?: return
		links.deleteByServerId(serverId)
		keys.delete(previous.encryptedApiToken)
	}

	suspend fun power(
		serverId: Long,
		action: GreencloudPowerAction,
		confirmed: Boolean
	): GreencloudPowerResult {
		if (action != GreencloudPowerAction.START && !confirmed) {
			return GreencloudPowerResult.NeedsConfirmation
		}
		val session = session(serverId) ?: return GreencloudPowerResult.NoAccount
		val result = try {
			client.power(session.apiToken, session.instanceId, action)
		} catch (e: Exception) {
			return GreencloudPowerResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return GreencloudPowerResult.Failed(result.message ?: "request failed")
		if (action != GreencloudPowerAction.START) {
			closeSessions(serverId)
		}
		return GreencloudPowerResult.Accepted
	}

	suspend fun status(serverId: Long): GreencloudStatusResult {
		val session = session(serverId) ?: return GreencloudStatusResult.Failed("no account")
		val result = try {
			client.status(session.apiToken, session.instanceId)
		} catch (e: Exception) {
			return GreencloudStatusResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return GreencloudStatusResult.Failed(result.message ?: "request failed")
		val power = result.powerStatus
			?: result.instances.firstOrNull { it.id == session.instanceId }?.powerStatus
			?: result.instances.firstOrNull()?.powerStatus
		if (power.isNullOrBlank()) return GreencloudStatusResult.Unknown
		return GreencloudStatusResult.Known(power)
	}

	suspend fun instanceInfo(serverId: Long): GreencloudInfoResult {
		val session = session(serverId) ?: return GreencloudInfoResult.NotBound
		val result = try {
			client.details(session.apiToken, session.instanceId)
		} catch (e: Exception) {
			return GreencloudInfoResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) {
			return GreencloudInfoResult.Failed(result.message ?: "request failed")
		}
		val info = result.info ?: return GreencloudInfoResult.Failed("empty info")
		if (info.isEmpty()) return GreencloudInfoResult.Failed("empty info")
		return GreencloudInfoResult.Known(info)
	}

	suspend fun resetPassword(serverId: Long, confirmed: Boolean): GreencloudResetResult {
		if (!confirmed) return GreencloudResetResult.NeedsConfirmation
		val session = session(serverId) ?: return GreencloudResetResult.NotBound
		val result = try {
			client.resetPassword(session.apiToken, session.instanceId)
		} catch (e: Exception) {
			return GreencloudResetResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return GreencloudResetResult.Failed(result.message ?: "request failed")
		val password = result.expectedPassword?.takeIf { it.isNotBlank() }
			?: return GreencloudResetResult.Failed("empty password")
		closeSessions(serverId)
		return GreencloudResetResult.Accepted(password)
	}

	private suspend fun session(serverId: Long): GreencloudSession? {
		val link = links.getByServerId(serverId) ?: return null
		return GreencloudSession(
			instanceId = link.instanceId,
			apiToken = keys.decrypt(link.encryptedApiToken)
		)
	}
}

private data class GreencloudSession(
	val instanceId: String,
	val apiToken: String
)
