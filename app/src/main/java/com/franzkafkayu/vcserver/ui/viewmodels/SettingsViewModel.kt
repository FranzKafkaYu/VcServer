package com.franzkafkayu.vcserver.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.AppSettings
import com.franzkafkayu.vcserver.models.LanguageMode
import com.franzkafkayu.vcserver.models.ProxyType
import com.franzkafkayu.vcserver.models.ThemeMode
import com.franzkafkayu.vcserver.services.DatabaseExportImportService
import com.franzkafkayu.vcserver.services.SettingsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
	val settings: AppSettings = AppSettings(),
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
	val showResetDialog: Boolean = false,
	val isExporting: Boolean = false,
	val isImporting: Boolean = false,
	val exportSuccess: Boolean = false,
	val importSuccess: Int? = null // 导入成功的服务器数量
)

/**
 * 设置 ViewModel
 */
class SettingsViewModel(
	private val settingsService: SettingsService,
	private val exportImportService: DatabaseExportImportService
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
					errorMessage = "UPDATE_THEME_FAILED:${e.message}"
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
				// 语言更改后需要重启Activity 才能生效
				onLanguageChanged()
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "UPDATE_LANGUAGE_FAILED:${e.message}"
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
					errorMessage = "UPDATE_CONNECTION_TIMEOUT_FAILED:${e.message}"
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
					errorMessage = "UPDATE_DEFAULT_PORT_FAILED:${e.message}"
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
					errorMessage = "UPDATE_REFRESH_INTERVAL_FAILED:${e.message}"
				)
			}
		}
	}

	/**
	 * 更新默认代理配置（仅作为模板，不启用）
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
					errorMessage = "UPDATE_DEFAULT_PROXY_FAILED:${e.message}"
				)
			}
		}
	}

	/**
	 * 显示重置对话
	 */
	fun showResetDialog() {
		_uiState.value = _uiState.value.copy(showResetDialog = true)
	}

	/**
	 * 隐藏重置对话
	 */
	fun hideResetDialog() {
		_uiState.value = _uiState.value.copy(showResetDialog = false)
	}

	/**
	 * 重置所有设置为默认状态
	 */
	fun resetToDefaults() {
		viewModelScope.launch {
			try {
				settingsService.resetToDefaults()
				hideResetDialog()
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "RESET_SETTINGS_FAILED:${e.message}"
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

	/**
	 * 导出数据库
	 */
	fun exportDatabase(context: Context, outputUri: Uri) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isExporting = true,
				exportSuccess = false,
				errorMessage = null
			)
			exportImportService.exportDatabase(context, outputUri)
				.onSuccess {
					_uiState.value = _uiState.value.copy(
						isExporting = false,
						exportSuccess = true
					)
				}
				.onFailure { e ->
					val errorKey = when (e.message) {
						"NO_SERVERS" -> "EXPORT_NO_SERVERS"
						"UNABLE_TO_OPEN_OUTPUT_STREAM" -> "EXPORT_OPEN_STREAM_FAILED"
						else -> "EXPORT_FAILED"
					}
					_uiState.value = _uiState.value.copy(
						isExporting = false,
						exportSuccess = false,
						errorMessage = "EXPORT_FAILED:$errorKey"
					)
				}
		}
	}

	/**
	 * 导入数据库
	 */
	fun importDatabase(context: Context, inputUri: Uri, importStrategy: Boolean) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isImporting = true,
				importSuccess = null,
				errorMessage = null
			)
			exportImportService.importDatabase(context, inputUri, importStrategy)
				.onSuccess { count ->
					_uiState.value = _uiState.value.copy(
						isImporting = false,
						importSuccess = count
					)
				}
				.onFailure { e ->
					val errorKey = when (e.message) {
						"UNABLE_TO_OPEN_INPUT_STREAM" -> "IMPORT_OPEN_STREAM_FAILED"
						"INVALID_FORMAT" -> "IMPORT_INVALID_FORMAT"
						else -> "IMPORT_FAILED"
					}
					_uiState.value = _uiState.value.copy(
						isImporting = false,
						importSuccess = null,
						errorMessage = "IMPORT_FAILED:$errorKey"
					)
				}
		}
	}

	/**
	 * 清除导出成功状态
	 */
	fun clearExportSuccess() {
		_uiState.value = _uiState.value.copy(exportSuccess = false)
	}

	/**
	 * 清除导入成功状态
	 */
	fun clearImportSuccess() {
		_uiState.value = _uiState.value.copy(importSuccess = null)
	}
}

