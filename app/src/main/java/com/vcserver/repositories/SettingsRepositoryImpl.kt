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
import com.vcserver.models.ProxyType
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
		private val DEFAULT_PROXY_TYPE_KEY = intPreferencesKey("default_proxy_type")
		private val DEFAULT_PROXY_HOST_KEY = stringPreferencesKey("default_proxy_host")
		private val DEFAULT_PROXY_PORT_KEY = intPreferencesKey("default_proxy_port")
		private val DEFAULT_PROXY_USERNAME_KEY = stringPreferencesKey("default_proxy_username")
		private val DEFAULT_PROXY_PASSWORD_KEY = stringPreferencesKey("default_proxy_password")

		private val DEFAULT_THEME = ThemeMode.SYSTEM.ordinal
		private val DEFAULT_LANGUAGE = LanguageMode.SYSTEM.ordinal
		private const val DEFAULT_CONNECTION_TIMEOUT = 30
		private const val DEFAULT_SSH_PORT = 22
		private const val DEFAULT_REFRESH_INTERVAL = 5
		private val DEFAULT_PROXY_TYPE = ProxyType.HTTP.ordinal
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
				defaultProxyType = ProxyType.values()[preferences[DEFAULT_PROXY_TYPE_KEY] ?: DEFAULT_PROXY_TYPE],
				defaultProxyHost = preferences[DEFAULT_PROXY_HOST_KEY] ?: "",
				defaultProxyPort = preferences[DEFAULT_PROXY_PORT_KEY] ?: DEFAULT_PROXY_PORT,
				defaultProxyUsername = preferences[DEFAULT_PROXY_USERNAME_KEY] ?: "",
				defaultProxyPassword = preferences[DEFAULT_PROXY_PASSWORD_KEY] ?: ""
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

	override suspend fun updateDefaultProxy(
		type: ProxyType,
		host: String,
		port: Int,
		username: String,
		password: String
	) {
		context.dataStore.edit { preferences ->
			preferences[DEFAULT_PROXY_TYPE_KEY] = type.ordinal
			preferences[DEFAULT_PROXY_HOST_KEY] = host
			preferences[DEFAULT_PROXY_PORT_KEY] = port
			preferences[DEFAULT_PROXY_USERNAME_KEY] = username
			preferences[DEFAULT_PROXY_PASSWORD_KEY] = password
		}
	}

	override suspend fun resetToDefaults() {
		context.dataStore.edit { preferences ->
			preferences[THEME_KEY] = DEFAULT_THEME
			preferences[LANGUAGE_KEY] = DEFAULT_LANGUAGE
			preferences[CONNECTION_TIMEOUT_KEY] = DEFAULT_CONNECTION_TIMEOUT
			preferences[DEFAULT_SSH_PORT_KEY] = DEFAULT_SSH_PORT
			preferences[REFRESH_INTERVAL_KEY] = DEFAULT_REFRESH_INTERVAL
			preferences[DEFAULT_PROXY_TYPE_KEY] = DEFAULT_PROXY_TYPE
			preferences[DEFAULT_PROXY_HOST_KEY] = ""
			preferences[DEFAULT_PROXY_PORT_KEY] = DEFAULT_PROXY_PORT
			preferences[DEFAULT_PROXY_USERNAME_KEY] = ""
			preferences[DEFAULT_PROXY_PASSWORD_KEY] = ""
		}
	}
}

