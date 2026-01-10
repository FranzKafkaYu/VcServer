package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.ProxyType
import com.franzkafkayu.vcserver.repositories.ServerGroupRepository
import com.franzkafkayu.vcserver.services.ServerManagementService
import com.franzkafkayu.vcserver.services.SettingsService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 添加/编辑服务器ViewModel
 */
class AddServerViewModel(
	private val serverManagementService: ServerManagementService,
	private val settingsService: SettingsService? = null,
	private val serverGroupRepository: ServerGroupRepository? = null,
	private val serverId: Long? = null // 如果�?null，则为新增模式；否则为编辑模�?
) : ViewModel() {
	private val _uiState = MutableStateFlow(AddServerUiState(isEditMode = serverId != null))
	val uiState: StateFlow<AddServerUiState> = _uiState.asStateFlow()

	init {
		// 如果是编辑模式，加载服务器信�?
		// 如果是新增模式，从设置中读取默认 SSH 端口
		if (serverId == null) {
			viewModelScope.launch {
				settingsService?.getSettings()?.first()?.let { settings ->
					_uiState.value = _uiState.value.copy(port = settings.defaultSshPort.toString())
				}
			}
		}
		serverId?.let { loadServerInfo(it) }
		// 加载分组列表
		loadGroups()
	}

	/**
	 * 加载分组列表
	 */
	private fun loadGroups() {
		if (serverGroupRepository == null) {
			return
		}
		viewModelScope.launch {
			serverGroupRepository.getAllGroups().collect { groups ->
				_uiState.value = _uiState.value.copy(groups = groups.sortedBy { it.name })
			}
		}
	}

	/**
	 * 加载服务器信息（编辑模式)
	 */
	private fun loadServerInfo(id: Long) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, error = null)
			val server = serverManagementService.getServerById(id)
			if (server != null) {
				// 解密敏感信息（如果需要显示）
				_uiState.value = _uiState.value.copy(
					name = server.name,
					host = server.host,
					port = server.port.toString(),
					username = server.username,
					authType = server.authType,
					password = "", // 密码不显示，需要重新输入
					privateKey = "", // 私钥不显示，需要重新输入
					keyPassphrase = server.keyPassphrase ?: "",
					selectedGroupId = server.groupId,
					proxyEnabled = server.proxyEnabled,
					proxyType = server.proxyType ?: ProxyType.HTTP,
					proxyHost = server.proxyHost ?: "",
					proxyPort = server.proxyPort?.toString() ?: "8080",
					proxyUsername = server.proxyUsername ?: "",
					proxyPassword = "", // 代理密码不显示，需要重新输入
					isLoading = false,
					error = null
				)
			} else {
				_uiState.value = _uiState.value.copy(
					isLoading = false,
					error = AppError.NetworkError("无法加载服务器信息")
				)
			}
		}
	}

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
	 * 更新代理启用状态
	 */
	fun updateProxyEnabled(enabled: Boolean) {
		_uiState.value = _uiState.value.copy(proxyEnabled = enabled)
	}

	/**
	 * 更新代理类型
	 */
	fun updateProxyType(type: ProxyType) {
		_uiState.value = _uiState.value.copy(proxyType = type)
	}

	/**
	 * 更新代理主机
	 */
	fun updateProxyHost(host: String) {
		_uiState.value = _uiState.value.copy(proxyHost = host)
	}

	/**
	 * 更新代理端口
	 */
	fun updateProxyPort(port: String) {
		_uiState.value = _uiState.value.copy(proxyPort = port)
	}

	/**
	 * 更新代理用户名
	 */
	fun updateProxyUsername(username: String) {
		_uiState.value = _uiState.value.copy(proxyUsername = username)
	}

	/**
	 * 更新代理密码
	 */
	fun updateProxyPassword(password: String) {
		_uiState.value = _uiState.value.copy(proxyPassword = password)
	}

	/**
	 * 更新分组选择
	 */
	fun updateSelectedGroup(groupId: Long?) {
		_uiState.value = _uiState.value.copy(selectedGroupId = groupId)
	}

	/**
	 * 切换代理密码可见性
	 */
	fun toggleProxyPasswordVisibility() {
		_uiState.value = _uiState.value.copy(proxyPasswordVisible = !_uiState.value.proxyPasswordVisible)
	}

	/**
	 * 测试连接
	 */
		fun testConnection() {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isTestingConnection = true, error = null)
			val state = _uiState.value
			
			val defaultPort = settingsService?.getSettings()?.first()?.defaultSshPort ?: 22
			val port = state.port.toIntOrNull() ?: defaultPort
			val result = serverManagementService.addServer(
				name = state.name,
				host = state.host,
				port = port,
				username = state.username,
				authType = state.authType,
				password = if (state.authType == AuthType.PASSWORD) state.password else null,
				privateKey = if (state.authType == AuthType.KEY) state.privateKey else null,
				keyPassphrase = if (state.authType == AuthType.KEY) state.keyPassphrase else null,
				groupId = state.selectedGroupId,
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
	 * 保存服务器（新增或编辑）
	 */
		fun saveServer(onSuccess: () -> Unit) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isSaving = true, error = null)
			val state = _uiState.value
			
			val defaultPort = settingsService?.getSettings()?.first()?.defaultSshPort ?: 22
			val port = state.port.toIntOrNull() ?: defaultPort
			
			// 解析代理设置
			val proxyPort = state.proxyPort.toIntOrNull() ?: 8080
			val proxyEnabled = state.proxyEnabled && state.proxyHost.isNotBlank() && state.proxyPort.toIntOrNull() in 1..65535

			val result = if (serverId != null) {
				// 编辑模式：更新服务器
				serverManagementService.updateServer(
					serverId = serverId,
					name = state.name,
					host = state.host,
					port = port,
					username = state.username,
					authType = state.authType,
					password = if (state.authType == AuthType.PASSWORD && state.password.isNotEmpty()) state.password else null,
					privateKey = if (state.authType == AuthType.KEY && state.privateKey.isNotEmpty()) state.privateKey else null,
					keyPassphrase = if (state.authType == AuthType.KEY) state.keyPassphrase else null,
					groupId = state.selectedGroupId,
					proxyEnabled = proxyEnabled,
					proxyType = if (proxyEnabled) state.proxyType else null,
					proxyHost = if (proxyEnabled) state.proxyHost else null,
					proxyPort = if (proxyEnabled) proxyPort else null,
					proxyUsername = if (proxyEnabled) state.proxyUsername else null,
					proxyPassword = if (proxyEnabled && state.proxyPassword.isNotEmpty()) state.proxyPassword else null
				)
			} else {
				// 新增模式：添加服务器
				serverManagementService.addServer(
					name = state.name,
					host = state.host,
					port = port,
					username = state.username,
					authType = state.authType,
					password = if (state.authType == AuthType.PASSWORD) state.password else null,
					privateKey = if (state.authType == AuthType.KEY) state.privateKey else null,
					groupId = state.selectedGroupId,
					keyPassphrase = if (state.authType == AuthType.KEY) state.keyPassphrase else null,
					proxyEnabled = proxyEnabled,
					proxyType = if (proxyEnabled) state.proxyType else null,
					proxyHost = if (proxyEnabled) state.proxyHost else null,
					proxyPort = if (proxyEnabled) proxyPort else null,
					proxyUsername = if (proxyEnabled) state.proxyUsername else null,
					proxyPassword = if (proxyEnabled && state.proxyPassword.isNotEmpty()) state.proxyPassword else null,
					testConnection = false
				).map { Unit }
			}

			result.fold(
				onSuccess = {
					// 保存成功后标记成功
					_uiState.value = _uiState.value.copy(saveSuccess = true, isSaving = false)
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
	 * 清除保存成功状态
	 */
	fun clearSaveSuccess() {
		_uiState.value = _uiState.value.copy(saveSuccess = false)
	}

	/**
	 * 重置状态（仅在新增模式下使用）
	 */
	fun reset() {
		// 保留编辑模式标志，只重置表单字段
		val isEditMode = _uiState.value.isEditMode
		_uiState.value = AddServerUiState(saveSuccess = false, isEditMode = isEditMode)
		// 重新从设置读取默认 SSH 端口
		viewModelScope.launch {
			settingsService?.getSettings()?.first()?.let { settings ->
				_uiState.value = _uiState.value.copy(port = settings.defaultSshPort.toString())
			}
		}
	}
}

/**
 * 添加/编辑服务器 UI 状态
 */
	data class AddServerUiState(
	val name: String = "",
	val host: String = "",
	val port: String = "", // 端口将在初始化时从设置中读取
	val username: String = "",
	val authType: AuthType = AuthType.PASSWORD,
	val password: String = "",
	val passwordVisible: Boolean = false,
	val privateKey: String = "",
	val keyPassphrase: String = "",
	val keyPassphraseVisible: Boolean = false,
	// 分组设置
	val groups: List<com.franzkafkayu.vcserver.models.ServerGroup> = emptyList(),
	val selectedGroupId: Long? = null,
	// 代理设置
	val proxyEnabled: Boolean = false,
	val proxyType: ProxyType = ProxyType.HTTP,
	val proxyHost: String = "",
	val proxyPort: String = "8080",
	val proxyUsername: String = "",
	val proxyPassword: String = "",
	val proxyPasswordVisible: Boolean = false,
	val isSaving: Boolean = false,
	val isTestingConnection: Boolean = false,
	val connectionTestSuccess: Boolean = false,
	val saveSuccess: Boolean = false,
	val isLoading: Boolean = false,
	val isEditMode: Boolean = false,
	val error: AppError? = null
)



