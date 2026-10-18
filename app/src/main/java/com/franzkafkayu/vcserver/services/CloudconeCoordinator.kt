package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.data.dao.CloudconeAccountDao
import com.franzkafkayu.vcserver.data.dao.CloudconeLinkDao
import com.franzkafkayu.vcserver.models.CloudconeAccount
import com.franzkafkayu.vcserver.models.CloudconeLink
import kotlinx.coroutines.flow.Flow

interface CloudconeAccountStore {
	suspend fun get(): CloudconeAccount?
	suspend fun save(account: CloudconeAccount)
	suspend fun delete()
	fun observe(): Flow<CloudconeAccount?>
}

class RoomCloudconeAccountStore(
	private val dao: CloudconeAccountDao
) : CloudconeAccountStore {
	override suspend fun get(): CloudconeAccount? = dao.get()
	override suspend fun save(account: CloudconeAccount) {
		dao.upsert(account.copy(id = CloudconeAccount.SINGLE_ID))
	}
	override suspend fun delete() = dao.delete()
	override fun observe(): Flow<CloudconeAccount?> = dao.observe()
}

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

sealed class CloudconeAccountResult {
	data object Success : CloudconeAccountResult()
	data class Failed(val message: String) : CloudconeAccountResult()
}

sealed class CloudconeBindResult {
	data object Success : CloudconeBindResult()
	data object OtherVendor : CloudconeBindResult()
	data object InstanceInUse : CloudconeBindResult()
	data object NoAccount : CloudconeBindResult()
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
	private val accounts: CloudconeAccountStore,
	private val links: CloudconeLinkStore,
	private val bandwagonLinks: BandwagonLinkStore,
	private val virtualizorLinks: VirtualizorLinkStore,
	private val client: CloudconeClient,
	private val keys: BandwagonKeyStore,
	private val closeSessions: (Long) -> Unit
) {
	fun observeAccount(): Flow<CloudconeAccount?> = accounts.observe()
	fun observeLinks(): Flow<List<CloudconeLink>> = links.observeAll()

	suspend fun saveAccount(apiKey: String, hash: String): CloudconeAccountResult {
		val trimmedKey = apiKey.trim()
		val trimmedHash = hash.trim()
		if (trimmedKey.isEmpty() || trimmedHash.isEmpty()) {
			return CloudconeAccountResult.Failed("API key and hash are required")
		}
		val listed = try {
			client.list(trimmedKey, trimmedHash)
		} catch (e: Exception) {
			return CloudconeAccountResult.Failed(e.message ?: "request failed")
		}
		if (!listed.ok) {
			return CloudconeAccountResult.Failed(listed.message ?: "request failed")
		}
		val encryptedKey = keys.encrypt(trimmedKey)
		val encryptedHash = keys.encrypt(trimmedHash)
		val previous = accounts.get()
		return try {
			accounts.save(
				CloudconeAccount(
					encryptedApiKey = encryptedKey,
					encryptedHash = encryptedHash
				)
			)
			if (previous != null) {
				if (previous.encryptedApiKey != encryptedKey) keys.delete(previous.encryptedApiKey)
				if (previous.encryptedHash != encryptedHash) keys.delete(previous.encryptedHash)
			}
			CloudconeAccountResult.Success
		} catch (e: Exception) {
			keys.delete(encryptedKey)
			keys.delete(encryptedHash)
			CloudconeAccountResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun deleteAccount() {
		val previous = accounts.get()
		links.deleteAll()
		accounts.delete()
		previous?.let {
			keys.delete(it.encryptedApiKey)
			keys.delete(it.encryptedHash)
		}
	}

	suspend fun onServerDeleted(serverId: Long) {
		links.deleteByServerId(serverId)
	}

	suspend fun bind(serverId: Long, instanceId: String): CloudconeBindResult {
		if (bandwagonLinks.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		if (virtualizorLinks.getByServerId(serverId) != null) return CloudconeBindResult.OtherVendor
		val occupied = links.getByInstanceId(instanceId)
		if (occupied != null && occupied.serverId != serverId) return CloudconeBindResult.InstanceInUse
		if (accounts.get() == null) return CloudconeBindResult.NoAccount
		val existing = links.getByServerId(serverId)
		if (existing != null) return CloudconeBindResult.OtherVendor
		return try {
			links.insert(CloudconeLink(serverId = serverId, instanceId = instanceId))
			CloudconeBindResult.Success
		} catch (e: Exception) {
			CloudconeBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		links.deleteByServerId(serverId)
	}

	suspend fun listInstances(): CloudconeCallResult {
		val secrets = secrets() ?: return CloudconeCallResult(ok = false, message = "no account", instances = emptyList())
		return try {
			client.list(secrets.first, secrets.second)
		} catch (e: Exception) {
			CloudconeCallResult(ok = false, message = e.message ?: "request failed", instances = emptyList())
		}
	}

	suspend fun power(
		instanceId: String,
		action: CloudconePowerAction,
		confirmed: Boolean
	): CloudconePowerResult {
		if (action != CloudconePowerAction.BOOT && !confirmed) {
			return CloudconePowerResult.NeedsConfirmation
		}
		val secrets = secrets() ?: return CloudconePowerResult.NoAccount
		val result = try {
			client.power(secrets.first, secrets.second, instanceId, action.endpoint())
		} catch (e: Exception) {
			return CloudconePowerResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return CloudconePowerResult.Failed(result.message ?: "request failed")
		if (action != CloudconePowerAction.BOOT) {
			links.getByInstanceId(instanceId)?.let { closeSessions(it.serverId) }
		}
		return CloudconePowerResult.Accepted
	}

	suspend fun instanceInfo(serverId: Long): CloudconeInfoResult {
		val link = links.getByServerId(serverId) ?: return CloudconeInfoResult.NotBound
		val secrets = secrets() ?: return CloudconeInfoResult.Failed("no account")
		val result = try {
			client.information(secrets.first, secrets.second, link.instanceId)
		} catch (e: Exception) {
			return CloudconeInfoResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) {
			return CloudconeInfoResult.Failed("${result.message ?: "request failed"} (id=${link.instanceId})")
		}
		val info = result.info
			?: return CloudconeInfoResult.Failed("empty info (id=${link.instanceId})")
		return CloudconeInfoResult.Known(info)
	}

	suspend fun status(instanceId: String): CloudconeStatusResult {
		val secrets = secrets() ?: return CloudconeStatusResult.Failed("no account")
		val result = try {
			client.information(secrets.first, secrets.second, instanceId)
		} catch (e: Exception) {
			return CloudconeStatusResult.Failed(e.message ?: "request failed")
		}
		if (!result.ok) return CloudconeStatusResult.Failed(result.message ?: "request failed")
		val power = result.instances.firstOrNull { it.id == instanceId }?.powerStatus
			?: result.instances.firstOrNull()?.powerStatus
		if (power.isNullOrBlank()) return CloudconeStatusResult.Unknown
		return CloudconeStatusResult.Known(power)
	}

	private suspend fun secrets(): Pair<String, String>? {
		val account = accounts.get() ?: return null
		return keys.decrypt(account.encryptedApiKey) to keys.decrypt(account.encryptedHash)
	}

	private fun CloudconePowerAction.endpoint(): String {
		return when (this) {
			CloudconePowerAction.BOOT -> HttpCloudconeClient.ACTION_BOOT
			CloudconePowerAction.SHUTDOWN -> HttpCloudconeClient.ACTION_SHUTDOWN
			CloudconePowerAction.REBOOT -> HttpCloudconeClient.ACTION_REBOOT
		}
	}
}
