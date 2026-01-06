package com.vcserver.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vcserver.models.AppSettings
import com.vcserver.models.LanguageMode
import com.vcserver.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 设置仓库实现
 */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

	companion object {
		private val THEME_KEY = intPreferencesKey("theme")
		private val LANGUAGE_KEY = intPreferencesKey("language")
		private val CONNECTION_TIMEOUT_KEY = intPreferencesKey("connection_timeout")
		private val DEFAULT_SSH_PORT_KEY = intPreferencesKey("default_ssh_port")
		private val REFRESH_INTERVAL_KEY = intPreferencesKey("refresh_interval")
		private val PROXY_ENABLED_KEY = booleanPreferencesKey("proxy_enabled")
		private val PROXY_HOST_KEY = stringPreferencesKey("proxy_host")
		private val PROXY_PORT_KEY = intPreferencesKey("proxy_port")
		private val PROXY_USERNAME_KEY = stringPreferencesKey("proxy_username")
		private val PROXY_PASSWORD_KEY = stringPreferencesKey("proxy_password")

		private val DEFAULT_THEME = ThemeMode.SYSTEM.ordinal
		private val DEFAULT_LANGUAGE = LanguageMode.SYSTEM.ordinal
		private const val DEFAULT_CONNECTION_TIMEOUT = 30
		private const val DEFAULT_SSH_PORT = 22
		private const val DEFAULT_REFRESH_INTERVAL = 5
		private const val DEFAULT_PROXY_ENABLED = false
		private const val DEFAULT_PROXY_PORT = 8080
	}

	override fun getSettings(): Flow<AppSettings> {
		return context.dataStore.data.map { preferences ->
			AppSettings(
				theme = ThemeMode.values()[preferences[THEME_KEY] ?: DEFAULT_THEME],
				language = LanguageMode.values()[preferences[LANGUAGE_KEY] ?: DEFAULT_LANGUAGE],
				connectionTimeout = preferences[CONNECTION_TIMEOUT_KEY] ?: DEFAULT_CONNECTION_TIMEOUT,
				defaultSshPort = preferences[DEFAULT_SSH_PORT_KEY] ?: DEFAULT_SSH_PORT,
				refreshInterval = preferences[REFRESH_INTERVAL_KEY] ?: DEFAULT_REFRESH_INTERVAL,
				proxyEnabled = preferences[PROXY_ENABLED_KEY] ?: DEFAULT_PROXY_ENABLED,
				proxyHost = preferences[PROXY_HOST_KEY] ?: "",
				proxyPort = preferences[PROXY_PORT_KEY] ?: DEFAULT_PROXY_PORT,
				proxyUsername = preferences[PROXY_USERNAME_KEY] ?: "",
				proxyPassword = preferences[PROXY_PASSWORD_KEY] ?: ""
			)
		}
	}

	override suspend fun updateTheme(theme: ThemeMode) {
		context.dataStore.edit { preferences ->
			preferences[THEME_KEY] = theme.ordinal
		}
	}

	override suspend fun updateLanguage(language: LanguageMode) {
		context.dataStore.edit { preferences ->
			preferences[LANGUAGE_KEY] = language.ordinal
		}
	}

	override suspend fun updateConnectionTimeout(timeout: Int) {
		context.dataStore.edit { preferences ->
			preferences[CONNECTION_TIMEOUT_KEY] = timeout
		}
	}

	override suspend fun updateDefaultSshPort(port: Int) {
		context.dataStore.edit { preferences ->
			preferences[DEFAULT_SSH_PORT_KEY] = port
		}
	}

	override suspend fun updateRefreshInterval(interval: Int) {
		context.dataStore.edit { preferences ->
			preferences[REFRESH_INTERVAL_KEY] = interval
		}
	}

	override suspend fun updateProxy(
		enabled: Boolean,
		host: String,
		port: Int,
		username: String,
		password: String
	) {
		context.dataStore.edit { preferences ->
			preferences[PROXY_ENABLED_KEY] = enabled
			preferences[PROXY_HOST_KEY] = host
			preferences[PROXY_PORT_KEY] = port
			preferences[PROXY_USERNAME_KEY] = username
			preferences[PROXY_PASSWORD_KEY] = password
		}
	}

	override suspend fun resetToDefaults() {
		context.dataStore.edit { preferences ->
			preferences[THEME_KEY] = DEFAULT_THEME
			preferences[LANGUAGE_KEY] = DEFAULT_LANGUAGE
			preferences[CONNECTION_TIMEOUT_KEY] = DEFAULT_CONNECTION_TIMEOUT
			preferences[DEFAULT_SSH_PORT_KEY] = DEFAULT_SSH_PORT
			preferences[REFRESH_INTERVAL_KEY] = DEFAULT_REFRESH_INTERVAL
			preferences[PROXY_ENABLED_KEY] = DEFAULT_PROXY_ENABLED
			preferences[PROXY_HOST_KEY] = ""
			preferences[PROXY_PORT_KEY] = DEFAULT_PROXY_PORT
			preferences[PROXY_USERNAME_KEY] = ""
			preferences[PROXY_PASSWORD_KEY] = ""
		}
	}
}

