package com.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.Session
import com.vcserver.models.Server
import com.vcserver.models.ServerStatus
import com.vcserver.services.ServerMonitoringService
import com.vcserver.utils.AppError
import com.vcserver.utils.toAppError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 服务器监控 ViewModel
 */
class ServerMonitoringViewModel(
	private val serverMonitoringService: ServerMonitoringService,
	val server: Server,
	val session: Session,
	private val refreshInterval: Long = 3000L // 默认3秒
) : ViewModel() {
	private val _uiState = MutableStateFlow(ServerMonitoringUiState())
	val uiState: StateFlow<ServerMonitoringUiState> = _uiState.asStateFlow()

	private var autoRefreshJob: Job? = null

	init {
		// 首次加载显示加载状态
		loadServerStatus(showLoading = true)
		// 启动自动刷新（不显示加载状态，避免界面闪烁）
		startAutoRefresh()
	}

	/**
	 * 加载服务器状态
	 * @param showLoading 是否显示加载状态（自动刷新时不显示，避免界面闪烁）
	 */
	fun loadServerStatus(showLoading: Boolean = true) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isLoading = showLoading,
				error = null
			)
			val result = serverMonitoringService.getServerStatus(session)
			result.fold(
				onSuccess = { status ->
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						serverStatus = status,
						error = null
					)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 断开连接
	 */
	fun disconnect() {
		serverMonitoringService.disconnect(session)
	}

	/**
	 * 开始自动刷新
	 */
	private fun startAutoRefresh() {
		autoRefreshJob = viewModelScope.launch {
			// 等待首次加载完成
			delay(refreshInterval)
			while (isActive) {
				// 只在非加载状态时刷新，避免重复刷新
				if (!_uiState.value.isLoading) {
					// 自动刷新时不显示加载状态，避免界面频繁闪烁
					loadServerStatus(showLoading = false)
				}
				delay(refreshInterval)
			}
		}
	}

	/**
	 * 停止自动刷新
	 */
	private fun stopAutoRefresh() {
		autoRefreshJob?.cancel()
		autoRefreshJob = null
	}

	/**
	 * 清除错误
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}

	override fun onCleared() {
		super.onCleared()
		stopAutoRefresh()
		disconnect()
	}
}

/**
 * 服务器监控 UI 状态
 */
data class ServerMonitoringUiState(
	val serverStatus: ServerStatus? = null,
	val isLoading: Boolean = false,
	val error: AppError? = null
)

