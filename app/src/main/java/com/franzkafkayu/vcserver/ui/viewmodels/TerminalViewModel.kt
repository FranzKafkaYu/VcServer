package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.services.TerminalService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.CommandAutoComplete
import com.franzkafkayu.vcserver.utils.CommandHistory
import com.franzkafkayu.vcserver.utils.SessionManager
import com.franzkafkayu.vcserver.utils.ShellTabInfo
import com.franzkafkayu.vcserver.utils.TerminalFont
import com.franzkafkayu.vcserver.utils.TerminalPreferences
import com.franzkafkayu.vcserver.utils.TerminalShellStore
import com.franzkafkayu.vcserver.utils.TerminalBuffer
import com.franzkafkayu.vcserver.utils.toAppError
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 终端 ViewModel
 */
class TerminalViewModel(
	private val terminalService: TerminalService,
	private val serverMonitoringService: ServerMonitoringService,
	val server: Server,
	var session: Session,
	private val sessionKey: String,
	private val terminalPreferences: TerminalPreferences
) : ViewModel() {
	private val _uiState = MutableStateFlow(TerminalUiState())
	val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

	private val commandHistory = CommandHistory()
	private var ptyCols = 80
	private var ptyRows = 24
	private var isReconnectingSession = false

	init {
		viewModelScope.launch {
			try {
				val savedFont = terminalPreferences.fontSizeSp()
				commandHistory.restore(terminalPreferences.history(server.id))
				_uiState.value = _uiState.value.copy(fontSizeSp = savedFont)
			} catch (_: Exception) {
			}
			TerminalShellStore.bindSession(sessionKey, session)
			if (!session.isConnected) {
				reconnectSession()
			} else {
				TerminalShellStore.ensureFirstTab(
					sessionKey,
					session,
					terminalService,
					ptyCols,
					ptyRows
				)
			}
			TerminalShellStore.observe(sessionKey).collect { snap ->
				_uiState.value = _uiState.value.copy(
					output = snap.output,
					terminalBuffer = snap.terminalBuffer,
					alternateScreen = snap.alternateScreen,
					frame = snap.frame,
					isConnecting = snap.isConnecting,
					isConnected = snap.isConnected,
					error = snap.error,
					tabs = snap.tabs,
					canCreateTab = snap.canCreateTab,
					maxReached = snap.maxReached
				)
			}
		}
	}

	fun newTab() {
		if (!TerminalShellStore.newTab(sessionKey, session, terminalService, ptyCols, ptyRows)) {
			_uiState.value = _uiState.value.copy(maxReached = true)
		}
	}

	fun switchTab(tabId: String) {
		TerminalShellStore.switchTab(sessionKey, tabId)
	}

	fun closeTab(tabId: String): Boolean {
		return TerminalShellStore.closeTab(sessionKey, tabId)
	}

	fun clearMaxReached() {
		TerminalShellStore.clearMaxReached(sessionKey)
		_uiState.value = _uiState.value.copy(maxReached = false)
	}

	fun increaseFont() = changeFont(TerminalFont.larger(_uiState.value.fontSizeSp))

	fun decreaseFont() = changeFont(TerminalFont.smaller(_uiState.value.fontSizeSp))

	private fun changeFont(next: Int) {
		if (next == _uiState.value.fontSizeSp) return
		_uiState.value = _uiState.value.copy(fontSizeSp = next)
		viewModelScope.launch {
			try {
				terminalPreferences.setFontSizeSp(next)
			} catch (_: Exception) {
			}
		}
	}

	fun updateTerminalSize(cols: Int, rows: Int) {
		val c = cols.coerceAtLeast(40)
		val r = rows.coerceAtLeast(10)
		if (c == ptyCols && r == ptyRows) return
		ptyCols = c
		ptyRows = r
		viewModelScope.launch {
			withContext(Dispatchers.IO) {
				TerminalShellStore.onPtySize(sessionKey, c, r)
			}
		}
	}

	fun sendArrow(letter: Char) {
		val buffer = TerminalShellStore.activeTab(sessionKey)?.buffer
		val sequence = if (buffer?.applicationCursorKeys() == true) {
			"\u001BO$letter"
		} else {
			"\u001B[$letter"
		}
		sendRawInput(sequence.toByteArray(Charsets.UTF_8))
	}

	fun sendCommand(command: String) {
		val trimmed = command.trim()
		if (trimmed.isEmpty()) return

		if (!session.isConnected) {
			if (!isReconnectingSession) {
				reconnectSession()
			}
			return
		}

		val tab = TerminalShellStore.activeTab(sessionKey)
		val channel = tab?.channel
		if (tab == null || channel == null || !terminalService.isConnected(channel)) {
			if (session.isConnected) {
				TerminalShellStore.reconnectActiveShell(sessionKey, session, terminalService)
			} else {
				_uiState.value = _uiState.value.copy(
					error = AppError.NetworkError("Shell is not connected")
				)
			}
			return
		}

		viewModelScope.launch {
			try {
				commandHistory.addCommand(trimmed)
				commandHistory.resetIndex()
				try {
					terminalPreferences.saveHistory(server.id, commandHistory.snapshot())
				} catch (_: Exception) {
				}

				tab.inputMutex.withLock {
					terminalService.sendCommand(channel, trimmed)
				}
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	fun sendRawInput(bytes: ByteArray) {
		val tab = TerminalShellStore.activeTab(sessionKey) ?: return
		val channel = tab.channel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}

		viewModelScope.launch {
			try {
				tab.inputMutex.withLock {
					terminalService.sendRawBytes(channel, bytes)
				}
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	fun sendInterrupt() = sendControlChar(0x03)

	fun sendEOF() = sendControlChar(0x04)

	fun sendSuspend() = sendControlChar(0x1A)

	fun sendClearScreen() = sendControlChar(0x0C)

	fun sendTab() = sendControlChar(0x09)

	fun sendEscape() = sendControlChar(0x1B)

	private fun sendControlChar(code: Int) {
		val tab = TerminalShellStore.activeTab(sessionKey) ?: return
		val channel = tab.channel ?: return
		if (!terminalService.isConnected(channel) || !session.isConnected) {
			return
		}
		viewModelScope.launch {
			try {
				tab.inputMutex.withLock {
					terminalService.sendControlChar(channel, code)
				}
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					error = e.toAppError()
				)
			}
		}
	}

	fun sendAnsiSequence(sequence: String) {
		sendRawInput(sequence.toByteArray(Charsets.UTF_8))
	}

	fun getPreviousCommand(): String? {
		return commandHistory.getPreviousCommand()
	}

	fun getNextCommand(): String {
		return commandHistory.getNextCommand() ?: ""
	}

	fun triggerAutoComplete(currentInput: String): String {
		return CommandAutoComplete.completeCommand(currentInput)
	}

	fun getMatchingCommands(input: String): List<String> {
		return CommandAutoComplete.getMatchingCommands(input)
	}

	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}

	fun reconnect() {
		if (!session.isConnected) {
			reconnectSession()
		} else {
			TerminalShellStore.reconnectActiveShell(sessionKey, session, terminalService)
		}
	}

	private fun reconnectSession() {
		if (isReconnectingSession) return
		isReconnectingSession = true

		viewModelScope.launch {
			val result = serverMonitoringService.connectToServer(server)
			result.fold(
				onSuccess = { newSession ->
					session = newSession
					SessionManager.putSession(sessionKey, newSession)
					TerminalShellStore.bindSession(sessionKey, newSession)
					isReconnectingSession = false
					if (TerminalShellStore.observe(sessionKey).value.tabs.isEmpty()) {
						TerminalShellStore.ensureFirstTab(
							sessionKey,
							newSession,
							terminalService,
							ptyCols,
							ptyRows
						)
					} else {
						TerminalShellStore.reconnectAllShells(sessionKey, newSession, terminalService)
					}
				},
				onFailure = { exception ->
					isReconnectingSession = false
					_uiState.value = _uiState.value.copy(
						error = exception.toAppError()
					)
				}
			)
		}
	}

	fun clearOutput() {
		val tab = TerminalShellStore.activeTab(sessionKey) ?: return
		tab.buffer.clearScreen()
		TerminalShellStore.switchTab(sessionKey, tab.id)
	}

	override fun onCleared() {
		super.onCleared()
	}
}

data class TerminalUiState(
	val output: String = "",
	val terminalBuffer: TerminalBuffer? = null,
	val alternateScreen: Boolean = false,
	val frame: Int = 0,
	val isConnecting: Boolean = false,
	val isConnected: Boolean = false,
	val error: AppError? = null,
	val fontSizeSp: Int = TerminalFont.DEFAULT_SP,
	val tabs: List<ShellTabInfo> = emptyList(),
	val canCreateTab: Boolean = true,
	val maxReached: Boolean = false
)
