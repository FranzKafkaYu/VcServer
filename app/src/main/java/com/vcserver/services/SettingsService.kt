package com.vcserver.services

import com.vcserver.models.AppSettings
import com.vcserver.models.LanguageMode
import com.vcserver.models.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 设置服务接口
 */
interface SettingsService {
	/**
	 * 获取设置流
	 */
	fun getSettings(): Flow<AppSettings>

	/**
	 * 更新主题
	 */
	suspend fun updateTheme(theme: ThemeMode)

	/**
	 * 更新语言
	 */
	suspend fun updateLanguage(language: LanguageMode)

	/**
	 * 更新连接超时时间
	 */
	suspend fun updateConnectionTimeout(timeout: Int)

	/**
	 * 更新默认 SSH 端口
	 */
	suspend fun updateDefaultSshPort(port: Int)

	/**
	 * 更新刷新间隔
	 */
	suspend fun updateRefreshInterval(interval: Int)

	/**
	 * 更新默认代理配置（仅作为模板，不启用）
	 */
	suspend fun updateDefaultProxy(
		type: com.vcserver.models.ProxyType,
		host: String,
		port: Int,
		username: String = "",
		password: String = ""
	)

	/**
	 * 重置所有设置为默认值
	 */
	suspend fun resetToDefaults()
}

