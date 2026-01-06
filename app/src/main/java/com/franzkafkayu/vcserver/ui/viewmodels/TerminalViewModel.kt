package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.services.TerminalService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.CommandAutoComplete
import com.franzkafkayu.vcserver.utils.CommandHistory
import com.franzkafkayu.vcserver.utils.SessionManager
import com.franzkafkayu.vcserver.utils.TerminalBuffer
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 终端 ViewModel
 */
class TerminalViewModel(
	private val terminalService: TerminalService,
	private val serverMonitoringService: ServerMonitoringService,
	val server: Server,
	var session: Session, // 改为 var，支持重连后更新
	private val sessionKey: String // 用于更新 SessionManager
) : ViewModel() {
	private val _uiState = MutableStateFlow(TerminalUiState())
	val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

	private var shellChannel: ChannelShell? = null
	private val commandHistory = CommandHistory()
	private val terminalBuffer = TerminalBuffer(rows = 2000, cols = 200)
	private var isReconnecting = false
	private var isReconnectingSession = false

	init {
		// 检�?SSH session 是否连接
		if (!session.isConnected) {
			// 自动尝试重连 SSH session
			reconnectSession()
		} else {
			connectShell()
		}
	}

	/**
	 * 连接Shell通道
	 */
	private fun connectShell() {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isConnecting = true, error = null)
			// 设置合理的终端大小（可以根据屏幕大小调整�?
			val result = terminalService.connectShell(session, rows = 50, cols = 120)
			result.fold(
				onSuccess = { channel ->
					shellChannel = channel
					_uiState.value = _uiState.value.copy(
						isConnecting = false,
						isConnected = true,
						error = null
					)
					// 开始收集输�?
					collectOutput(channel)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isConnecting = false,
						isConnected = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 收集Shell输出
	 */
	private fun collectOutput(channel: ChannelShell) {
		viewModelScope.launch {
			terminalService.getOutputFlow(channel).collect { output ->
				// 处理 ANSI 转义序列并更新缓冲区
				processAnsiOutput(output)
				
				// 更新 UI 状�?
				_uiState.value = _uiState.value.copy(
					terminalBuffer = terminalBuffer,
					output = terminalBuffer.getPlainText() // 保留纯文本用于兼�?
				)
			}
			// 输出流结束，连接断开
			// 检�?SSH session 是否还连接，如果连接则尝试重�?Shell channel
			if (session.isConnected && !isReconnecting) {
				reconnectShell()
			} else {
				_uiState.value = _uiState.value.copy(
					isConnected = false,
					error = AppError.NetworkError("Shell connection closed")
				)
			}
		}
	}

	/**
	 * 重新连接 Shell channel
	 */
	private fun reconnectShell() {
		if (isReconnecting) return
		isReconnecting = true

		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isConnecting = true,
				isConnected = false,
				error = null
			)

			val result = terminalService.connectShell(session, rows = 50, cols = 120)
			result.fold(
				onSuccess = { channel ->
					shellChannel = channel
					isReconnecting = false
					_uiState.value = _uiState.value.copy(
						isConnecting = false,
						isConnected = true,
						error = null
					)
					// 重新开始收集输�?
					collectOutput(channel)
				},
				onFailure = { exception ->
					isReconnecting = false
					_uiState.value = _uiState.value.copy(
						isConnecting = false,
						isConnected = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 处理 ANSI 输出
	 */
	private fun processAnsiOutput(output: String) {
		// 检查是否有清屏命令
		if (output.contains("\u001B[2J") || output.contains("\u001B[H")) {
			terminalBuffer.clearScreen()
			// 移除清屏命令后继续处�?
			val cleaned = output.replace(Regex("\u001B\\[2J|\u001B\\[H"), "")
			if (cleaned.isNotEmpty()) {
				terminalBuffer.write(cleaned)
			}
		} else {
			terminalBuffer.write(output)
		}
	}

	/**
	 * 发送命�?
	 */
	fun sendCommand(command: String) {
		val trimmed = command.trim()
		if (trimmed.isEmpty()) return

		// 检�?SSH session 是否连接
		if (!session.isConnected) {
			if (!isReconnectingSession) {
				reconnectSession()
			}
			return
		}

		val channel = shellChannel
		if (channel == null || !terminalService.isConnected(channel)) {
			// 如果 SSH session 还连接，尝试重连 Shell channel
			if (session.isConnected && !isReconnecting) {
				reconnectShell()
			} else {
				_uiState.value = _uiState.value.copy(
					error = AppError.NetworkError("Shell is not connected")
				)
			}
			return
		}

		viewModelScope.launch {
			try {
				// 添加到历史记�?
				commandHistory.addCommand(trimmed)
				commandHistory.resetIndex()

				// 发送命�?
				terminalService.sendCommand(channel, trimmed)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 发送实时字符输入（用于支持光标移动和实时编辑）
	 */
	fun sendRawInput(bytes: ByteArray) {
		val channel = shellChannel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				terminalService.sendRawBytes(channel, bytes)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 发�?Ctrl+C（中断信号）
	 */
	fun sendInterrupt() {
		val channel = shellChannel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				terminalService.sendControlChar(channel, 0x03) // Ctrl+C
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 发�?Ctrl+D（EOF 信号�?
	 */
	fun sendEOF() {
		val channel = shellChannel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				terminalService.sendControlChar(channel, 0x04) // Ctrl+D
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 发�?Ctrl+L（清屏）
	 */
	fun sendClearScreen() {
		val channel = shellChannel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				terminalService.sendControlChar(channel, 0x0C) // Ctrl+L
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 发�?ANSI 转义序列（用于光标移动等�?
	 */
	fun sendAnsiSequence(sequence: String) {
		val channel = shellChannel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				terminalService.sendRawBytes(channel, sequence.toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	/**
	 * 获取上一条历史命�?
	 */
	fun getPreviousCommand(): String? {
		val command = commandHistory.getPreviousCommand()
		return command
	}

	/**
	 * 获取下一条历史命�?
	 */
	fun getNextCommand(): String {
		val command = commandHistory.getNextCommand()
		return command ?: ""
	}

	/**
	 * 触发自动补全
	 */
	fun triggerAutoComplete(currentInput: String): String {
		return CommandAutoComplete.completeCommand(currentInput)
	}

	/**
	 * 获取匹配的命令列表（用于显示候选）
	 */
	fun getMatchingCommands(input: String): List<String> {
		return CommandAutoComplete.getMatchingCommands(input)
	}

	/**
	 * 清除错误
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}

	/**
	 * 手动重连（供 UI 调用�?
	 * 如果 SSH session 断开，先重连 session，然后重�?Shell channel
	 */
	fun reconnect() {
		if (!session.isConnected) {
			reconnectSession()
		} else {
			reconnectShell()
		}
	}

	/**
	 * 重新连接 SSH session
	 */
	private fun reconnectSession() {
		if (isReconnectingSession) return
		isReconnectingSession = true

		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isConnecting = true,
				isConnected = false,
				error = null
			)

			val result = serverMonitoringService.connectToServer(server)
			result.fold(
				onSuccess = { newSession ->
					// 更新 session
					session = newSession
					// 更新 SessionManager 中的 session
					SessionManager.putSession(sessionKey, newSession)
					isReconnectingSession = false
					// 连接成功后，连接 Shell channel
					connectShell()
				},
				onFailure = { exception ->
					isReconnectingSession = false
					_uiState.value = _uiState.value.copy(
						isConnecting = false,
						isConnected = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 清空输出
	 */
	fun clearOutput() {
		terminalBuffer.clearScreen()
		_uiState.value = _uiState.value.copy(
			output = "",
			terminalBuffer = terminalBuffer
		)
	}

	override fun onCleared() {
		super.onCleared()
		shellChannel?.let { terminalService.disconnectShell(it) }
	}
}

/**
 * 终端 UI 状�?
 */
data class TerminalUiState(
	val output: String = "",
	val terminalBuffer: TerminalBuffer? = null,
	val isConnecting: Boolean = false,
	val isConnected: Boolean = false,
	val error: AppError? = null
)

