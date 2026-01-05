package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AppSettings
import com.franzkafkayu.vcserver.models.LanguageMode
import com.franzkafkayu.vcserver.models.ThemeMode
import com.franzkafkayu.vcserver.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * 设置服务实现
 */
class SettingsServiceImpl(
	private val settingsRepository: SettingsRepository
) : SettingsService {

	override fun getSettings(): Flow<AppSettings> {
		return settingsRepository.getSettings()
	}

	override suspend fun updateTheme(theme: ThemeMode) {
		settingsRepository.updateTheme(theme)
	}

	override suspend fun updateLanguage(language: LanguageMode) {
		settingsRepository.updateLanguage(language)
	}

	override suspend fun updateConnectionTimeout(timeout: Int) {
		if (timeout <= 0) {
			throw ValidationException("CONNECTION_TIMEOUT_INVALID")
		}
		settingsRepository.updateConnectionTimeout(timeout)
	}

	override suspend fun updateDefaultSshPort(port: Int) {
		if (port !in 1..65535) {
			throw ValidationException("SSH_PORT_INVALID")
		}
		settingsRepository.updateDefaultSshPort(port)
	}

	override suspend fun updateRefreshInterval(interval: Int) {
		if (interval < 1) {
			throw ValidationException("REFRESH_INTERVAL_INVALID")
		}
		settingsRepository.updateRefreshInterval(interval)
	}

	override suspend fun updateDefaultProxy(
		type: com.franzkafkayu.vcserver.models.ProxyType,
		host: String,
		port: Int,
		username: String,
		password: String
	) {
		// 默认代理配置仅作为模板，不进行严格验证
		// 实际使用时的验证会在服务器级别的代理设置中进行
		if (host.isNotBlank() && port !in 1..65535) {
			throw ValidationException("PROXY_PORT_INVALID")
		}
		settingsRepository.updateDefaultProxy(type, host, port, username, password)
	}

	override suspend fun resetToDefaults() {
		settingsRepository.resetToDefaults()
	}
}
