package com.franzkafkayu.vcserver.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerStatus
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.services.SftpFileTransferService
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
 * 服务器监监控ViewModel
 */
class ServerMonitoringViewModel(
	private val serverMonitoringService: ServerMonitoringService,
	private val sftpFileTransferService: SftpFileTransferService? = null,
	val server: Server,
	var session: Session, // 改为 var，支持重连后更新
	private val refreshInterval: Long = 3000L // 默认3s
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
	 * 加载服务器状态
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
	 * 用户主动断开时，从连接池和 SessionManager 同时移除
	 */
	fun disconnect() {
		// 用户主动断开连接，从连接池移除
		serverMonitoringService.disconnectFromServer(server.id)
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
	 * 手动重连 SSH session（供 UI 调用)
	 */
	fun reconnect() {
		if (!isReconnecting) {
			reconnectSession()
		}
	}

	/**
	 * 上传文件到远程服务器
	 */
		fun uploadFile(context: Context, localUri: Uri, remotePath: String) {
			viewModelScope.launch {
				if (sftpFileTransferService == null) {
					_uiState.value = _uiState.value.copy(
						fileTransferError = "SFTP_SERVICE_NOT_AVAILABLE"
					)
					return@launch
				}

				_uiState.value = _uiState.value.copy(
					isFileTransferring = true,
					fileTransferProgress = 0f,
					fileTransferError = null,
					fileTransferType = "upload"
				)

			val result = sftpFileTransferService.uploadFile(
				session = session,
				localUri = localUri,
				remotePath = remotePath,
				progressCallback = object : SftpFileTransferService.FileTransferProgressCallback {
					override fun onProgress(transferred: Long, total: Long) {
						_uiState.value = _uiState.value.copy(
							fileTransferProgress = if (total > 0) {
								(transferred.toFloat() / total.toFloat())
							} else {
								0f
							},
							fileTransferTransferred = transferred,
							fileTransferTotal = total
						)
					}
				}
			)

			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isFileTransferring = false,
						fileTransferProgress = 1f,
						fileTransferSuccess = true
						// 保持 fileTransferType，以便显示正确的成功消息
					)
				},
				onFailure = { exception ->
					val errorMessage = exception.message ?: "Unknown error"
					val errorKey = when {
						errorMessage.startsWith("SESSION_NOT_CONNECTED") -> "SESSION_NOT_CONNECTED"
						errorMessage.startsWith("UNABLE_TO_CREATE_SFTP_CHANNEL") -> "UNABLE_TO_CREATE_SFTP_CHANNEL"
						errorMessage.startsWith("UNABLE_TO_OPEN_LOCAL_FILE") -> "UNABLE_TO_OPEN_LOCAL_FILE"
						errorMessage.startsWith("PERMISSION_DENIED") -> "PERMISSION_DENIED"
						errorMessage.startsWith("PARENT_DIR_NOT_EXISTS") -> "PARENT_DIR_NOT_EXISTS"
						errorMessage.startsWith("REMOTE_PATH_IS_DIRECTORY") -> "REMOTE_PATH_IS_DIRECTORY"
						else -> "UPLOAD_FAILED"
					}
					_uiState.value = _uiState.value.copy(
						isFileTransferring = false,
						fileTransferProgress = 0f,
						fileTransferError = "$errorKey:$errorMessage",
						fileTransferType = null
					)
				}
			)
		}
	}

	/**
	 * 从远程服务器下载文件
	 */
		fun downloadFile(context: Context, remotePath: String, localUri: Uri) {
			viewModelScope.launch {
				if (sftpFileTransferService == null) {
					_uiState.value = _uiState.value.copy(
						fileTransferError = "SFTP_SERVICE_NOT_AVAILABLE"
					)
					return@launch
				}

				_uiState.value = _uiState.value.copy(
					isFileTransferring = true,
					fileTransferProgress = 0f,
					fileTransferError = null,
					fileTransferType = "download"
				)

			val result = sftpFileTransferService.downloadFile(
				session = session,
				remotePath = remotePath,
				localUri = localUri,
				progressCallback = object : SftpFileTransferService.FileTransferProgressCallback {
					override fun onProgress(transferred: Long, total: Long) {
						_uiState.value = _uiState.value.copy(
							fileTransferProgress = if (total > 0) {
								(transferred.toFloat() / total.toFloat())
							} else {
								0f
							},
							fileTransferTransferred = transferred,
							fileTransferTotal = total
						)
					}
				}
			)

			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isFileTransferring = false,
						fileTransferProgress = 1f,
						fileTransferSuccess = true
						// 保持 fileTransferType，以便显示正确的成功消息
					)
				},
				onFailure = { exception ->
					val errorMessage = exception.message ?: "Unknown error"
					val errorKey = when {
						errorMessage.startsWith("SESSION_NOT_CONNECTED") -> "SESSION_NOT_CONNECTED"
						errorMessage.startsWith("UNABLE_TO_CREATE_SFTP_CHANNEL") -> "UNABLE_TO_CREATE_SFTP_CHANNEL"
						errorMessage.startsWith("UNABLE_TO_OPEN_LOCAL_FILE") -> "UNABLE_TO_OPEN_LOCAL_FILE"
						errorMessage.startsWith("PERMISSION_DENIED") -> "PERMISSION_DENIED"
						errorMessage.startsWith("FILE_NOT_EXISTS") -> "FILE_NOT_EXISTS"
						else -> "DOWNLOAD_FAILED"
					}
					_uiState.value = _uiState.value.copy(
						isFileTransferring = false,
						fileTransferProgress = 0f,
						fileTransferError = "$errorKey:$errorMessage",
						fileTransferType = null
					)
				}
			)
		}
	}

	/**
	 * 清除文件传输成功状态
	 */
	fun clearFileTransferSuccess() {
		_uiState.value = _uiState.value.copy(fileTransferSuccess = false)
	}

	/**
	 * 清除文件传输错误
	 */
	fun clearFileTransferError() {
		_uiState.value = _uiState.value.copy(fileTransferError = null)
	}

	override fun onCleared() {
		super.onCleared()
		stopAutoRefresh()
		disconnect()
	}
}

/**
 * 服务器监控UI状态
 */
data class ServerMonitoringUiState(
	val serverStatus: ServerStatus? = null,
	val isLoading: Boolean = false,
	val error: AppError? = null,
	// 文件传输状态
	val isFileTransferring: Boolean = false,
	val fileTransferProgress: Float = 0f,
	val fileTransferTransferred: Long = 0L,
	val fileTransferTotal: Long = 0L,
	val fileTransferSuccess: Boolean = false,
	val fileTransferError: String? = null,
	val fileTransferType: String? = null // "upload" 或 "download"
)

