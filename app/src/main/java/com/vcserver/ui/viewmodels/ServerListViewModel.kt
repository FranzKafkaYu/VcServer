package com.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vcserver.models.Server
import com.vcserver.services.ServerManagementService
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
	private val serverManagementService: ServerManagementService
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
	val error: AppError? = null
)



