package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.services.ServerManagementService
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 服务器列�?ViewModel
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
	 * 加载服务器列�?
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
	 * 显示删除确认对话�?
	 */
	fun showDeleteConfirmDialog(server: Server) {
		_uiState.value = _uiState.value.copy(
			serverToDelete = server
		)
	}

	/**
	 * 取消删除
	 */
	fun cancelDelete() {
		_uiState.value = _uiState.value.copy(
			serverToDelete = null
		)
	}

	/**
	 * 确认删除服务�?
	 */
	fun confirmDeleteServer() {
		val server = _uiState.value.serverToDelete ?: return
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, serverToDelete = null)
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
					com.franzkafkayu.vcserver.utils.SessionManager.putSession(sessionKey, session)
					
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
			// 获取服务器状态（包含系统信息�?
			val statusResult = serverMonitoringService.getServerStatus(session)
			statusResult.fold(
				onSuccess = { status ->
					status.systemInfo?.let { systemInfo ->
						// 构建系统版本字符串（例如 "Ubuntu 22.04"�?
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

	/**
	 * 更新服务器排序顺�?
	 */
	fun updateServerOrder(servers: List<Server>) {
		viewModelScope.launch {
			val result = serverManagementService.updateServerOrder(servers)
			result.fold(
				onSuccess = {
					// 排序更新成功，列表会自动刷新（通过 Flow�?
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 进入选择模式
	 */
	fun enterSelectionMode(initialServerId: Long? = null) {
		_uiState.value = _uiState.value.copy(
			isSelectionMode = true,
			selectedServerIds = if (initialServerId != null) setOf(initialServerId) else emptySet()
		)
	}

	/**
	 * 退出选择模式
	 */
	fun exitSelectionMode() {
		_uiState.value = _uiState.value.copy(
			isSelectionMode = false,
			selectedServerIds = emptySet()
		)
	}

	/**
	 * 切换服务器选择状�?
	 */
	fun toggleServerSelection(serverId: Long) {
		val currentSelected = _uiState.value.selectedServerIds
		_uiState.value = _uiState.value.copy(
			selectedServerIds = if (currentSelected.contains(serverId)) {
				currentSelected - serverId
			} else {
				currentSelected + serverId
			}
		)
	}

	/**
	 * 全�?取消全�?
	 */
	fun toggleSelectAll() {
		val currentSelected = _uiState.value.selectedServerIds
		val allServerIds = _uiState.value.servers.map { it.id }.toSet()
		_uiState.value = _uiState.value.copy(
			selectedServerIds = if (currentSelected.size == allServerIds.size) {
				emptySet()
			} else {
				allServerIds
			}
		)
	}

	/**
	 * 显示批量删除确认对话�?
	 */
	fun showBatchDeleteConfirmDialog() {
		val selectedIds = _uiState.value.selectedServerIds
		val serversToDelete = _uiState.value.servers.filter { it.id in selectedIds }
		if (serversToDelete.isNotEmpty()) {
			_uiState.value = _uiState.value.copy(
				showBatchDeleteConfirm = true
			)
		}
	}

	/**
	 * 取消批量删除
	 */
	fun cancelBatchDelete() {
		_uiState.value = _uiState.value.copy(
			showBatchDeleteConfirm = false
		)
	}

	/**
	 * 确认批量删除选中的服务器
	 */
	fun confirmDeleteSelectedServers() {
		viewModelScope.launch {
			val selectedIds = _uiState.value.selectedServerIds
			val serversToDelete = _uiState.value.servers.filter { it.id in selectedIds }
			
			if (serversToDelete.isEmpty()) return@launch
			
			_uiState.value = _uiState.value.copy(
				isLoading = true,
				showBatchDeleteConfirm = false
			)
			
			var successCount = 0
			var failCount = 0
			
			serversToDelete.forEach { server ->
				val result = serverManagementService.deleteServer(server)
				if (result.isSuccess) {
					successCount++
				} else {
					failCount++
				}
			}
			
			_uiState.value = _uiState.value.copy(
				isLoading = false,
				isSelectionMode = false,
				selectedServerIds = emptySet(),
				error = if (failCount > 0) {
					AppError.NetworkError("成功删除 $successCount 个，失败 $failCount 个")
				} else {
					null
				}
			)
		}
	}
}

/**
 * 服务器列表 UI 状态
 */
data class ServerListUiState(
	val servers: List<Server> = emptyList(),
	val isLoading: Boolean = true,
	val connectingServerId: Long? = null,
	val error: AppError? = null,
	val isSelectionMode: Boolean = false,
	val selectedServerIds: Set<Long> = emptySet(),
	val serverToDelete: Server? = null, // 待删除的服务器（用于显示确认对话框）
	val showBatchDeleteConfirm: Boolean = false // 是否显示批量删除确认对话框
)