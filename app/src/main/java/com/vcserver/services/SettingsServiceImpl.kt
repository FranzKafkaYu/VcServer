package com.vcserver.services

import com.vcserver.models.AppSettings
import com.vcserver.models.LanguageMode
import com.vcserver.models.ThemeMode
import com.vcserver.repositories.SettingsRepository
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
		require(timeout > 0) { "连接超时时间必须大于 0" }
		settingsRepository.updateConnectionTimeout(timeout)
	}

	override suspend fun updateDefaultSshPort(port: Int) {
		require(port in 1..65535) { "SSH 端口必须在 1-65535 范围内" }
		settingsRepository.updateDefaultSshPort(port)
	}

	override suspend fun updateRefreshInterval(interval: Int) {
		require(interval >= 1) { "刷新间隔必须大于等于 1 秒" }
		settingsRepository.updateRefreshInterval(interval)
	}

	override suspend fun updateProxy(
		enabled: Boolean,
		host: String,
		port: Int,
		username: String,
		password: String
	) {
		if (enabled) {
			require(host.isNotBlank()) { "代理主机不能为空" }
			require(port in 1..65535) { "代理端口必须在 1-65535 范围内" }
		}
		settingsRepository.updateProxy(enabled, host, port, username, password)
	}

	override suspend fun resetToDefaults() {
		settingsRepository.resetToDefaults()
	}
}

