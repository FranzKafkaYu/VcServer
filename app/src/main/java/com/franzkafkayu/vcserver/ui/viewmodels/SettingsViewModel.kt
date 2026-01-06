package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.AppSettings
import com.franzkafkayu.vcserver.models.LanguageMode
import com.franzkafkayu.vcserver.models.ProxyType
import com.franzkafkayu.vcserver.models.ThemeMode
import com.franzkafkayu.vcserver.services.SettingsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置界面 UI 状�?
 */
data class SettingsUiState(
	val settings: AppSettings = AppSettings(),
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
	val showResetDialog: Boolean = false
)

/**
 * 设置 ViewModel
 */
class SettingsViewModel(
	private val settingsService: SettingsService
) : ViewModel() {

	private val _uiState = MutableStateFlow(SettingsUiState())
	val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

	init {
		loadSettings()
	}

	/**
	 * 加载设置
	 */
	private fun loadSettings() {
		viewModelScope.launch {
			settingsService.getSettings().collect { settings ->
				_uiState.value = _uiState.value.copy(settings = settings)
			}
		}
	}

	/**
	 * 更新主题
	 */
	fun updateTheme(theme: ThemeMode) {
		viewModelScope.launch {
			try {
				settingsService.updateTheme(theme)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新主题失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 更新语言
	 */
	fun updateLanguage(language: LanguageMode, onLanguageChanged: () -> Unit = {}) {
		viewModelScope.launch {
			try {
				settingsService.updateLanguage(language)
				// 语言更改后需要重�?Activity 才能生效
				onLanguageChanged()
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新语言失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 更新连接超时时间
	 */
	fun updateConnectionTimeout(timeout: Int) {
		viewModelScope.launch {
			try {
				settingsService.updateConnectionTimeout(timeout)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新连接超时时间失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 更新默认 SSH 端口
	 */
	fun updateDefaultSshPort(port: Int) {
		viewModelScope.launch {
			try {
				settingsService.updateDefaultSshPort(port)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新默认端口失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 更新刷新间隔
	 */
	fun updateRefreshInterval(interval: Int) {
		viewModelScope.launch {
			try {
				settingsService.updateRefreshInterval(interval)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新刷新间隔失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 更新默认代理配置（仅作为模板，不启用�?
	 */
	fun updateDefaultProxy(
		type: ProxyType,
		host: String,
		port: Int,
		username: String = "",
		password: String = ""
	) {
		viewModelScope.launch {
			try {
				settingsService.updateDefaultProxy(type, host, port, username, password)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "更新默认代理配置失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 显示重置对话�?
	 */
	fun showResetDialog() {
		_uiState.value = _uiState.value.copy(showResetDialog = true)
	}

	/**
	 * 隐藏重置对话�?
	 */
	fun hideResetDialog() {
		_uiState.value = _uiState.value.copy(showResetDialog = false)
	}

	/**
	 * 重置所有设置为默认�?
	 */
	fun resetToDefaults() {
		viewModelScope.launch {
			try {
				settingsService.resetToDefaults()
				hideResetDialog()
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "重置设置失败: ${e.message}"
				)
			}
		}
	}

	/**
	 * 清除错误消息
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(errorMessage = null)
	}
}

