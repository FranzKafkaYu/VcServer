package com.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vcserver.models.AuthType
import com.vcserver.services.ServerManagementService
import com.vcserver.utils.AppError
import com.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 添加服务器 ViewModel
 */
class AddServerViewModel(
	private val serverManagementService: ServerManagementService
) : ViewModel() {
	private val _uiState = MutableStateFlow(AddServerUiState())
	val uiState: StateFlow<AddServerUiState> = _uiState.asStateFlow()

	/**
	 * 更新服务器名称
	 */
	fun updateName(name: String) {
		_uiState.value = _uiState.value.copy(name = name)
	}

	/**
	 * 更新主机地址
	 */
	fun updateHost(host: String) {
		_uiState.value = _uiState.value.copy(host = host)
	}

	/**
	 * 更新端口
	 */
	fun updatePort(port: String) {
		_uiState.value = _uiState.value.copy(port = port)
	}

	/**
	 * 更新用户名
	 */
	fun updateUsername(username: String) {
		_uiState.value = _uiState.value.copy(username = username)
	}

	/**
	 * 更新认证方式
	 */
	fun updateAuthType(authType: AuthType) {
		_uiState.value = _uiState.value.copy(authType = authType)
	}

	/**
	 * 更新密码
	 */
	fun updatePassword(password: String) {
		_uiState.value = _uiState.value.copy(password = password)
	}

	/**
	 * 更新私钥
	 */
	fun updatePrivateKey(privateKey: String) {
		_uiState.value = _uiState.value.copy(privateKey = privateKey)
	}

	/**
	 * 更新密钥密码
	 */
	fun updateKeyPassphrase(passphrase: String) {
		_uiState.value = _uiState.value.copy(keyPassphrase = passphrase)
	}

	/**
	 * 切换密码可见性
	 */
	fun togglePasswordVisibility() {
		_uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
	}

	/**
	 * 切换密钥密码可见性
	 */
	fun toggleKeyPassphraseVisibility() {
		_uiState.value = _uiState.value.copy(keyPassphraseVisible = !_uiState.value.keyPassphraseVisible)
	}

	/**
	 * 测试连接
	 */
	fun testConnection() {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isTestingConnection = true, error = null)
			val state = _uiState.value
			
			val port = state.port.toIntOrNull() ?: 22
			val result = serverManagementService.addServer(
				name = state.name,
				host = state.host,
				port = port,
				username = state.username,
				authType = state.authType,
				password = if (state.authType == AuthType.PASSWORD) state.password else null,
				privateKey = if (state.authType == AuthType.KEY) state.privateKey else null,
				keyPassphrase = if (state.authType == AuthType.KEY) state.keyPassphrase else null,
				testConnection = true
			)

			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isTestingConnection = false,
						connectionTestSuccess = true
					)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isTestingConnection = false,
						connectionTestSuccess = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 保存服务器
	 */
	fun saveServer(onSuccess: () -> Unit) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isSaving = true, error = null)
			val state = _uiState.value
			
			val port = state.port.toIntOrNull() ?: 22
			val result = serverManagementService.addServer(
				name = state.name,
				host = state.host,
				port = port,
				username = state.username,
				authType = state.authType,
				password = if (state.authType == AuthType.PASSWORD) state.password else null,
				privateKey = if (state.authType == AuthType.KEY) state.privateKey else null,
				keyPassphrase = if (state.authType == AuthType.KEY) state.keyPassphrase else null,
				testConnection = false
			)

			result.fold(
				onSuccess = {
					// 保存成功后标记成功并重置状态
					_uiState.value = AddServerUiState(saveSuccess = true)
					// 不在这里调用 onSuccess，让 LaunchedEffect 处理导航
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isSaving = false,
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
		_uiState.value = _uiState.value.copy(error = null, connectionTestSuccess = false)
	}

	/**
	 * 重置状态
	 */
	fun reset() {
		_uiState.value = AddServerUiState(saveSuccess = false)
	}
}

/**
 * 添加服务器 UI 状态
 */
	data class AddServerUiState(
	val name: String = "",
	val host: String = "",
	val port: String = "22",
	val username: String = "",
	val authType: AuthType = AuthType.PASSWORD,
	val password: String = "",
	val passwordVisible: Boolean = false,
	val privateKey: String = "",
	val keyPassphrase: String = "",
	val keyPassphraseVisible: Boolean = false,
	val isSaving: Boolean = false,
	val isTestingConnection: Boolean = false,
	val connectionTestSuccess: Boolean = false,
	val saveSuccess: Boolean = false,
	val error: AppError? = null
)



