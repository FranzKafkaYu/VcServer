package com.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.Session
import com.vcserver.models.Server
import com.vcserver.services.ServerManagementService
import com.vcserver.services.ServerMonitoringService
import com.vcserver.utils.AppError
import com.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 服务器列表 ViewModel
 */
class ServerListViewModel(
	private val serverManagementService: ServerManagementService,
	private val serverMonitoringService: ServerMonitoringService
) : ViewModel() {
	private val _uiState = MutableStateFlow(ServerListUiState())
	val uiState: StateFlow<ServerListUiState> = _uiState.asStateFlow()

	init {
		loadServers()
	}

	/**
	 * 加载服务器列表
	 */
	private fun loadServers() {
		viewModelScope.launch {
			serverManagementService.getAllServers().collect { servers ->
				_uiState.value = _uiState.value.copy(
					servers = servers,
					isLoading = false
				)
			}
		}
	}

	/**
	 * 删除服务器
	 */
	fun deleteServer(server: Server) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true)
			val result = serverManagementService.deleteServer(server)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isLoading = false,
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
	 * 连接到服务器
	 */
	fun connectToServer(server: Server, onSuccess: (String) -> Unit) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				connectingServerId = server.id,
				error = null
			)
			val result = serverMonitoringService.connectToServer(server)
			result.fold(
				onSuccess = { session ->
					val sessionKey = "${server.id}_${System.currentTimeMillis()}"
					com.vcserver.utils.SessionManager.putSession(sessionKey, session)
					
					// 获取系统信息并更新服务器
					updateServerSystemInfo(server, session)
					
					_uiState.value = _uiState.value.copy(
						connectingServerId = null
					)
					onSuccess(sessionKey)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						connectingServerId = null,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 更新服务器的系统版本信息
	 */
	private suspend fun updateServerSystemInfo(server: Server, session: com.jcraft.jsch.Session) {
		try {
			// 获取服务器状态（包含系统信息）
			val statusResult = serverMonitoringService.getServerStatus(session)
			statusResult.fold(
				onSuccess = { status ->
					status.systemInfo?.let { systemInfo ->
						// 构建系统版本字符串（例如 "Ubuntu 22.04"）
						val systemVersion = "${systemInfo.osName} ${systemInfo.osVersion}".trim()
						if (systemVersion.isNotEmpty() && systemVersion != "Unknown Unknown") {
							// 更新服务器的系统版本信息
							val updatedServer = server.copy(
								systemVersion = systemVersion,
								updatedAt = System.currentTimeMillis()
							)
							serverManagementService.updateServer(updatedServer)
						}
					}
				},
				onFailure = {
					// 获取系统信息失败，不影响连接流程
				}
			)
		} catch (e: Exception) {
			// 更新失败，不影响连接流程
		}
	}

	/**
	 * 清除错误
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}
}

/**
 * 服务器列表 UI 状态
 */
data class ServerListUiState(
	val servers: List<Server> = emptyList(),
	val isLoading: Boolean = true,
	val connectingServerId: Long? = null,
	val error: AppError? = null
)



