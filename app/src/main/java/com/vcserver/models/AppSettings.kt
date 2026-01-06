package com.vcserver.models

/**
 * 应用设置数据模型
 */
data class AppSettings(
	val theme: ThemeMode = ThemeMode.SYSTEM,
	val language: LanguageMode = LanguageMode.SYSTEM,
	val connectionTimeout: Int = 30, // 秒
	val defaultSshPort: Int = 22,
	val refreshInterval: Int = 5, // 秒
	val proxyEnabled: Boolean = false,
	val proxyHost: String = "",
	val proxyPort: Int = 8080,
	val proxyUsername: String = "",
	val proxyPassword: String = ""
)

/**
 * 主题模式
 */
enum class ThemeMode {
	LIGHT,   // 浅色主题
	DARK,    // 深色主题
	SYSTEM   // 跟随系统
}

/**
 * 语言模式
 */
enum class LanguageMode {
	CHINESE,  // 中文
	ENGLISH,  // 英文
	SYSTEM    // 跟随系统
}

