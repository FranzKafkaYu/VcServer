package com.franzkafkayu.vcserver.services

import android.util.Log
import com.franzkafkayu.vcserver.data.dao.BandwagonLinkDao
import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

interface BandwagonLinkStore {
	suspend fun getByServerId(serverId: Long): BandwagonLink?
	suspend fun getByVeid(veid: String): BandwagonLink?
	suspend fun upsert(link: BandwagonLink)
	suspend fun deleteByServerId(serverId: Long)
	fun observeBoundServerIds(): Flow<Set<Long>>
}

class RoomBandwagonLinkStore(
	private val dao: BandwagonLinkDao
) : BandwagonLinkStore {
	override suspend fun getByServerId(serverId: Long): BandwagonLink? = dao.getByServerId(serverId)

	override suspend fun getByVeid(veid: String): BandwagonLink? = dao.getByVeid(veid)

	override suspend fun upsert(link: BandwagonLink) {
		val existing = dao.getByServerId(link.serverId)
		if (existing == null) {
			dao.insert(link.copy(id = 0))
		} else {
			dao.update(link.copy(id = existing.id))
		}
	}

	override suspend fun deleteByServerId(serverId: Long) {
		dao.deleteByServerId(serverId)
	}

	override fun observeBoundServerIds(): Flow<Set<Long>> {
		return dao.observeServerIds().map { it.toSet() }
	}
}

interface BandwagonKeyStore {
	fun encrypt(apiKey: String): String
	fun decrypt(path: String): String
	fun delete(path: String)
}

class SecureBandwagonKeyStore(
	private val secureStorage: SecureStorage
) : BandwagonKeyStore {
	override fun encrypt(apiKey: String): String = secureStorage.encryptPassword(apiKey)

	override fun decrypt(path: String): String = secureStorage.decryptPassword(path)

	override fun delete(path: String) {
		secureStorage.deleteEncryptedFile(path)
		if (File(path).exists()) {
			Log.w(TAG, "搬瓦工密钥文件仍在")
		}
	}

	companion object {
		private const val TAG = "BandwagonKeyStore"
	}
}

enum class BandwagonPowerAction {
	START,
	STOP,
	RESTART
}

sealed class BandwagonBindResult {
	data object Success : BandwagonBindResult()
	data object VeidInUse : BandwagonBindResult()
	data object OtherVendor : BandwagonBindResult()
	data class Failed(val message: String) : BandwagonBindResult()
}

sealed class BandwagonPowerResult {
	data object Accepted : BandwagonPowerResult()
	data object NeedsConfirmation : BandwagonPowerResult()
	data object NotBound : BandwagonPowerResult()
	data class Failed(val message: String) : BandwagonPowerResult()
}

sealed class BandwagonStatusResult {
	data class Known(val veStatus: String) : BandwagonStatusResult()
	data object Unknown : BandwagonStatusResult()
	data class Failed(val message: String) : BandwagonStatusResult()
}

sealed class BandwagonServiceInfoResult {
	data class Known(val info: BandwagonServiceInfo) : BandwagonServiceInfoResult()
	data object NotBound : BandwagonServiceInfoResult()
	data class Failed(val message: String) : BandwagonServiceInfoResult()
}

sealed class BandwagonMigrateLocationsResult {
	data class Known(val locations: BandwagonMigrateLocations) : BandwagonMigrateLocationsResult()
	data object NotBound : BandwagonMigrateLocationsResult()
	data class Failed(val message: String) : BandwagonMigrateLocationsResult()
}

sealed class BandwagonMigrateStartResult {
	data class Accepted(val start: BandwagonMigrateStart) : BandwagonMigrateStartResult()
	data object NeedsConfirmation : BandwagonMigrateStartResult()
	data object NotBound : BandwagonMigrateStartResult()
	data class Failed(val message: String) : BandwagonMigrateStartResult()
}

sealed class BandwagonResetRootResult {
	data class Accepted(val password: String) : BandwagonResetRootResult()
	data object NeedsConfirmation : BandwagonResetRootResult()
	data object NotBound : BandwagonResetRootResult()
	data class Failed(val message: String) : BandwagonResetRootResult()
}

sealed class BandwagonUsageResult {
	data class Known(val stats: BandwagonUsageStats) : BandwagonUsageResult()
	data object NotBound : BandwagonUsageResult()
	data class Failed(val message: String) : BandwagonUsageResult()
}

class BandwagonPowerCoordinator(
	private val store: BandwagonLinkStore,
	private val client: BandwagonClient,
	private val keys: BandwagonKeyStore,
	private val blockedByOtherVendor: suspend (Long) -> Boolean = { false },
	private val closeSessions: (Long) -> Unit
) {
	fun observeBoundServerIds(): Flow<Set<Long>> = store.observeBoundServerIds()

	suspend fun bind(serverId: Long, veid: String, apiKey: String): BandwagonBindResult {
		val trimmedVeid = veid.trim()
		val trimmedKey = apiKey.trim()
		if (trimmedVeid.isEmpty() || trimmedKey.isEmpty()) {
			return BandwagonBindResult.Failed("VEID and API key are required")
		}
		if (blockedByOtherVendor(serverId)) {
			return BandwagonBindResult.OtherVendor
		}
		val occupied = store.getByVeid(trimmedVeid)
		if (occupied != null && occupied.serverId != serverId) {
			return BandwagonBindResult.VeidInUse
		}
		val info = try {
			client.call(
				HttpBandwagonClient.ACTION_INFO,
				trimmedVeid,
				trimmedKey,
				HttpBandwagonClient.TIMEOUT_POWER_MS
			)
		} catch (e: Exception) {
			return BandwagonBindResult.Failed(e.message ?: "request failed")
		}
		if (info.error != 0) {
			return BandwagonBindResult.Failed(info.message ?: "error ${info.error}")
		}
		val encrypted = keys.encrypt(trimmedKey)
		val previous = store.getByServerId(serverId)
		return try {
			store.upsert(
				BandwagonLink(
					id = previous?.id ?: 0,
					serverId = serverId,
					veid = trimmedVeid,
					encryptedApiKey = encrypted
				)
			)
			if (previous != null && previous.encryptedApiKey != encrypted) {
				keys.delete(previous.encryptedApiKey)
			}
			BandwagonBindResult.Success
		} catch (e: Exception) {
			keys.delete(encrypted)
			BandwagonBindResult.Failed(e.message ?: "save failed")
		}
	}

	suspend fun unbind(serverId: Long) {
		onServerDeleted(serverId)
	}

	suspend fun onServerDeleted(serverId: Long) {
		val previous = store.getByServerId(serverId) ?: return
		store.deleteByServerId(serverId)
		keys.delete(previous.encryptedApiKey)
	}

	suspend fun power(
		serverId: Long,
		action: BandwagonPowerAction,
		confirmed: Boolean
	): BandwagonPowerResult {
		if (action != BandwagonPowerAction.START && !confirmed) {
			return BandwagonPowerResult.NeedsConfirmation
		}
		val link = store.getByServerId(serverId) ?: return BandwagonPowerResult.NotBound
		val result = try {
			client.call(action.endpoint(), link.veid, keys.decrypt(link.encryptedApiKey), HttpBandwagonClient.TIMEOUT_POWER_MS)
		} catch (e: Exception) {
			return BandwagonPowerResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonPowerResult.Failed(result.message ?: "error ${result.error}")
		}
		if (action != BandwagonPowerAction.START) {
			closeSessions(serverId)
		}
		return BandwagonPowerResult.Accepted
	}

	suspend fun status(serverId: Long): BandwagonStatusResult {
		val link = store.getByServerId(serverId) ?: return BandwagonStatusResult.Failed("not bound")
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_LIVE,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_LIVE_MS
			)
		} catch (e: Exception) {
			return BandwagonStatusResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonStatusResult.Failed(result.message ?: "error ${result.error}")
		}
		val veStatus = result.veStatus
		if (veStatus.isNullOrBlank()) {
			return BandwagonStatusResult.Unknown
		}
		return BandwagonStatusResult.Known(veStatus)
	}

	suspend fun serviceInfo(serverId: Long): BandwagonServiceInfoResult {
		val link = store.getByServerId(serverId) ?: return BandwagonServiceInfoResult.NotBound
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_INFO,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_POWER_MS
			)
		} catch (e: Exception) {
			return BandwagonServiceInfoResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonServiceInfoResult.Failed(result.message ?: "error ${result.error}")
		}
		val info = result.info ?: return BandwagonServiceInfoResult.Failed("empty info")
		return BandwagonServiceInfoResult.Known(info)
	}

	suspend fun migrateLocations(serverId: Long): BandwagonMigrateLocationsResult {
		val link = store.getByServerId(serverId) ?: return BandwagonMigrateLocationsResult.NotBound
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_MIGRATE_LOCATIONS,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_POWER_MS
			)
		} catch (e: Exception) {
			return BandwagonMigrateLocationsResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonMigrateLocationsResult.Failed(result.message ?: "error ${result.error}")
		}
		val locations = result.migrateLocations ?: return BandwagonMigrateLocationsResult.Failed("empty locations")
		return BandwagonMigrateLocationsResult.Known(locations)
	}

	suspend fun migrateStart(
		serverId: Long,
		location: String,
		confirmed: Boolean
	): BandwagonMigrateStartResult {
		if (!confirmed) return BandwagonMigrateStartResult.NeedsConfirmation
		val trimmed = location.trim()
		if (trimmed.isEmpty()) {
			return BandwagonMigrateStartResult.Failed("location is required")
		}
		val link = store.getByServerId(serverId) ?: return BandwagonMigrateStartResult.NotBound
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_MIGRATE_START,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_MIGRATE_MS,
				mapOf("location" to trimmed)
			)
		} catch (e: Exception) {
			return BandwagonMigrateStartResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonMigrateStartResult.Failed(result.message ?: "error ${result.error}")
		}
		val start = result.migrateStart ?: BandwagonMigrateStart(notificationEmail = null, newIps = emptyList())
		closeSessions(serverId)
		return BandwagonMigrateStartResult.Accepted(start)
	}

	suspend fun resetRootPassword(serverId: Long, confirmed: Boolean): BandwagonResetRootResult {
		if (!confirmed) return BandwagonResetRootResult.NeedsConfirmation
		val link = store.getByServerId(serverId) ?: return BandwagonResetRootResult.NotBound
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_RESET_ROOT,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_POWER_MS
			)
		} catch (e: Exception) {
			return BandwagonResetRootResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonResetRootResult.Failed(result.message ?: "error ${result.error}")
		}
		val password = result.password?.takeIf { it.isNotBlank() }
			?: return BandwagonResetRootResult.Failed("empty password")
		return BandwagonResetRootResult.Accepted(password)
	}

	suspend fun usageStats(serverId: Long): BandwagonUsageResult {
		val link = store.getByServerId(serverId) ?: return BandwagonUsageResult.NotBound
		val result = try {
			client.call(
				HttpBandwagonClient.ACTION_USAGE,
				link.veid,
				keys.decrypt(link.encryptedApiKey),
				HttpBandwagonClient.TIMEOUT_USAGE_MS
			)
		} catch (e: Exception) {
			return BandwagonUsageResult.Failed(e.message ?: "request failed")
		}
		if (result.error != 0) {
			return BandwagonUsageResult.Failed(result.message ?: "error ${result.error}")
		}
		val usage = result.usage ?: return BandwagonUsageResult.Failed("empty stats")
		if (usage.isEmpty()) return BandwagonUsageResult.Failed("empty stats")
		return BandwagonUsageResult.Known(usage)
	}

	fun closeBoundSessions(serverId: Long) {
		closeSessions(serverId)
	}

	private fun BandwagonPowerAction.endpoint(): String {
		return when (this) {
			BandwagonPowerAction.START -> HttpBandwagonClient.ACTION_START
			BandwagonPowerAction.STOP -> HttpBandwagonClient.ACTION_STOP
			BandwagonPowerAction.RESTART -> HttpBandwagonClient.ACTION_RESTART
		}
	}
}
