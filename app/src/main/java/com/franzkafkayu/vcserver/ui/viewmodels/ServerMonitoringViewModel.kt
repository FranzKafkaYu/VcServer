package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerStatus
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 服务器监�?ViewModel
 */
class ServerMonitoringViewModel(
	private val serverMonitoringService: ServerMonitoringService,
	val server: Server,
	var session: Session, // 改为 var，支持重连后更新
	private val refreshInterval: Long = 3000L // 默认3�?
) : ViewModel() {
	private val _uiState = MutableStateFlow(ServerMonitoringUiState())
	val uiState: StateFlow<ServerMonitoringUiState> = _uiState.asStateFlow()

	private var autoRefreshJob: Job? = null
	private var isReconnecting = false

	init {
		// 检�?session 是否连接
		if (!session.isConnected) {
			// 如果断开，先尝试重连
			reconnectSession()
		} else {
			// 首次加载显示加载状�?
			loadServerStatus(showLoading = true)
			// 启动自动刷新（不显示加载状态，避免界面闪烁�?
			startAutoRefresh()
		}
	}

	/**
	 * 加载服务器状�?
	 * @param showLoading 是否显示加载状态（自动刷新时不显示，避免界面闪烁）
	 */
	fun loadServerStatus(showLoading: Boolean = true) {
		viewModelScope.launch {
			// 检�?session 是否连接
			if (!session.isConnected) {
				// 尝试自动重连
				if (!isReconnecting) {
					reconnectSession()
				}
				return@launch
			}

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
					// 如果错误是连接相关，尝试重连
					val errorMessage = exception.message ?: ""
					if (errorMessage.contains("not connected", ignoreCase = true) ||
						errorMessage.contains("connection", ignoreCase = true)) {
						if (!isReconnecting) {
							reconnectSession()
						}
					} else {
						_uiState.value = _uiState.value.copy(
							isLoading = false,
							error = exception.toAppError()
						)
					}
				}
			)
		}
	}

	/**
	 * 重新连接 SSH session
	 */
	private fun reconnectSession() {
		if (isReconnecting) return
		isReconnecting = true

		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isLoading = true,
				error = null
			)

			val result = serverMonitoringService.connectToServer(server)
			result.fold(
				onSuccess = { newSession ->
					// 更新 session
					session = newSession
					isReconnecting = false
					// 重新加载状�?
					loadServerStatus(showLoading = false)
					// 确保自动刷新在运�?
					if (autoRefreshJob == null || !autoRefreshJob!!.isActive) {
						startAutoRefresh()
					}
				},
				onFailure = { exception ->
					isReconnecting = false
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
	 * 开始自动刷�?
	 */
	private fun startAutoRefresh() {
		autoRefreshJob = viewModelScope.launch {
			// 等待首次加载完成
			delay(refreshInterval)
			while (isActive) {
				// 只在非加载状态时刷新，避免重复刷�?
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

	/**
	 * 手动重连 SSH session（供 UI 调用�?
	 */
	fun reconnect() {
		if (!isReconnecting) {
			reconnectSession()
		}
	}

	override fun onCleared() {
		super.onCleared()
		stopAutoRefresh()
		disconnect()
	}
}

/**
 * 服务器监�?UI 状�?
 */
data class ServerMonitoringUiState(
	val serverStatus: ServerStatus? = null,
	val isLoading: Boolean = false,
	val error: AppError? = null
)

