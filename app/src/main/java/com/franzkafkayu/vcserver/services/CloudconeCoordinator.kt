package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.data.dao.CloudconeLinkDao
import com.franzkafkayu.vcserver.models.CloudconeLink
import kotlinx.coroutines.flow.Flow

interface CloudconeLinkStore {
	suspend fun getByServerId(serverId: Long): CloudconeLink?
	suspend fun getByInstanceId(instanceId: String): CloudconeLink?
	suspend fun all(): List<CloudconeLink>
	suspend fun insert(link: CloudconeLink)
	suspend fun deleteByServerId(serverId: Long)
	suspend fun deleteAll()
	fun observeAll(): Flow<List<CloudconeLink>>
}

class RoomCloudconeLinkStore(
	private val dao: CloudconeLinkDao
) : CloudconeLinkStore {
	override suspend fun getByServerId(serverId: Long): CloudconeLink? = dao.getByServerId(serverId)
	override suspend fun getByInstanceId(instanceId: String): CloudconeLink? = dao.getByInstanceId(instanceId)
	override suspend fun all(): List<CloudconeLink> = dao.all()
	override suspend fun insert(link: CloudconeLink) {
		dao.insert(link.copy(id = 0))
	}
	override suspend fun deleteByServerId(serverId: Long) = dao.deleteByServerId(serverId)
	override suspend fun deleteAll() = dao.deleteAll()
	override fun observeAll(): Flow<List<CloudconeLink>> = dao.observeAll()
}

enum class CloudconePowerAction { BOOT, SHUTDOWN, REBOOT }

sealed class CloudconeBindResult {
	data object Success : CloudconeBindResult()
	data object OtherVendor : CloudconeBindResult()
	data object InstanceInUse : CloudconeBindResult()
	data class Failed(val message: String) : CloudconeBindResult()
}

sealed class CloudconePowerResult {
	data object Accepted : CloudconePowerResult()
	data object NeedsConfirmation : CloudconePowerResult()
	data object NoAccount : CloudconePowerResult()
	data class Failed(val message: String) : CloudconePowerResult()
}

sealed class CloudconeStatusResult {
	data class Known(val powerStatus: String) : CloudconeStatusResult()
	data object Unknown : CloudconeStatusResult()
	data class Failed(val message: String) : CloudconeStatusResult()
}

sealed class CloudconeInfoResult {
	data class Known(val info: CloudconeInstanceInfo) : CloudconeInfoResult()
	data object NotBound : CloudconeInfoResult()
	data class Failed(val message: String) : CloudconeInfoResult()
}

class CloudconeCoordinator(
	private val links: CloudconeLinkStore,
	private val bandwagonLinks: BandwagonLinkStore,
	private val virtualizorLinks: VirtualizorLinkStore,
	private val greencloudLinks: GreencloudLinkStore,
	private val client: CloudconeClient,
	private val keys: BandwagonKeyStore,
	private val closeSessions: (Long) -> Unit
) {
	fun observeLinks(): Flow<List<CloudconeLink>> = links.observeAll()

	suspend fun listInstances(apiKey: String, hash: String): CloudconeCallResult {
		val trimmedKey = apiKey.trim()
		val trimmedHash = hash.trim()
		if (trimmedKey.isEmpty() || trimmedHash.isEmpty()) {
			return CloudconeCallResult(ok = false, message = "API key and hash are required", instances = emptyList())
		}
		return try {
			client.list(trimmedKey, trimmedHash)
		} catch (e: Exception) {
			CloudconeCallResult(ok = false, message = e.message ?: "request failed", instances = emptyList())
		}
	}

	suspend fun bind(
		serverId: Long,
		instanceId: String,
		apiKey: String,
		hash: String
	): CloudconeBindResult {
		if (bandwagonLinks.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		if (virtualizorLinks.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		if (greencloudLinks.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		if (links.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		val occupied = links.getByInstanceId(instanceId)
		if (occupied != null && occupied.serverId != serverId) return CloudconeBindResult.InstanceInUse
		val encryptedKey = keys.encrypt(apiKey.trim())
		val encryptedHash = keys.encrypt(hash.trim())
		return try {
			links.insert(
				CloudconeLink(
					serverId = serverId,
					instanceId = instanceId,
					encryptedApiKey = encryptedKey,
					encryptedHash = encryptedHash
				)
			)
			CloudconeBindResult.Success
		} catch (e: Exception) {
			keys.delete(encryptedKey)
			keys.delete(encryptedHash)
			CloudconeBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		onServerDeleted(serverId)
	}

	suspend fun onServerDeleted(serverId: Long) {
		val previous = links.getByServerId(serverId) ?: return
		links.deleteByServerId(serverId)
		keys.delete(previous.encryptedApiKey)
		keys.delete(previous.encryptedHash)
	}

	suspend fun power(
		serverId: Long,
		action: CloudconePowerAction,
		confirmed: Boolean
	): CloudconePowerResult {
		if (action != CloudconePowerAction.BOOT && !confirmed) {
			return CloudconePowerResult.NeedsConfirmation
		}
		val session = session(serverId) ?: return CloudconePowerResult.NoAccount
		val result = try {
			client.power(session.apiKey, session.hash, session.instanceId, action.endpoint())
		} catch (e: Exception) {
			return CloudconePowerResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return CloudconePowerResult.Failed(result.message ?: "request failed")
		if (action != CloudconePowerAction.BOOT) {
			closeSessions(serverId)
		}
		return CloudconePowerResult.Accepted
	}

	suspend fun instanceInfo(serverId: Long): CloudconeInfoResult {
		val session = session(serverId) ?: return CloudconeInfoResult.NotBound
		val result = try {
			client.information(session.apiKey, session.hash, session.instanceId)
		} catch (e: Exception) {
			return CloudconeInfoResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) {
			return CloudconeInfoResult.Failed("${result.message ?: "request failed"} (id=${session.instanceId})")
		}
		val info = result.info
			?: return CloudconeInfoResult.Failed("empty info (id=${session.instanceId})")
		return CloudconeInfoResult.Known(info)
	}

	suspend fun status(serverId: Long): CloudconeStatusResult {
		val session = session(serverId) ?: return CloudconeStatusResult.Failed("no account")
		val result = try {
			client.information(session.apiKey, session.hash, session.instanceId)
		} catch (e: Exception) {
			return CloudconeStatusResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return CloudconeStatusResult.Failed(result.message ?: "request failed")
		val power = result.instances.firstOrNull { it.id == session.instanceId }?.powerStatus
			?: result.instances.firstOrNull()?.powerStatus
			?: result.info?.powerStatus
		if (power.isNullOrBlank()) return CloudconeStatusResult.Unknown
		return CloudconeStatusResult.Known(power)
	}

	private suspend fun session(serverId: Long): CloudconeSession? {
		val link = links.getByServerId(serverId) ?: return null
		return CloudconeSession(
			instanceId = link.instanceId,
			apiKey = keys.decrypt(link.encryptedApiKey),
			hash = keys.decrypt(link.encryptedHash)
		)
	}

	private fun CloudconePowerAction.endpoint(): String {
		return when (this) {
			CloudconePowerAction.BOOT -> HttpCloudconeClient.ACTION_BOOT
			CloudconePowerAction.SHUTDOWN -> HttpCloudconeClient.ACTION_SHUTDOWN
			CloudconePowerAction.REBOOT -> HttpCloudconeClient.ACTION_REBOOT
		}
	}
}

private data class CloudconeSession(
	val instanceId: String,
	val apiKey: String,
	val hash: String
)
