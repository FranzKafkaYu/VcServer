package com.franzkafkayu.vcserver.utils

import com.franzkafkayu.vcserver.services.TerminalService
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 按 sessionKey 持有多路 ChannelShell，存活不依赖 TerminalViewModel。
 */
object TerminalShellStore {
	const val MAX_TABS = 4

	private val holders = ConcurrentHashMap<String, SessionShells>()

	fun observe(sessionKey: String): StateFlow<ShellSessionSnapshot> {
		return holder(sessionKey).snapshot.asStateFlow()
	}

	fun ensureFirstTab(
		sessionKey: String,
		session: Session,
		terminalService: TerminalService,
		cols: Int,
		rows: Int
	) {
		val h = holder(sessionKey)
		synchronized(h) {
			if (h.tabs.isNotEmpty()) {
				h.publishLocked()
				return
			}
			addTabLocked(h, session, terminalService, cols, rows)
		}
	}

	fun newTab(
		sessionKey: String,
		session: Session,
		terminalService: TerminalService,
		cols: Int,
		rows: Int
	): Boolean {
		val h = holder(sessionKey)
		synchronized(h) {
			if (h.tabs.size >= MAX_TABS) {
				h.maxReached = true
				h.publishLocked()
				return false
			}
			h.maxReached = false
			addTabLocked(h, session, terminalService, cols, rows)
			return true
		}
	}

	fun switchTab(sessionKey: String, tabId: String) {
		val h = holders[sessionKey] ?: return
		synchronized(h) {
			if (h.tabs.none { it.id == tabId }) return
			h.activeId = tabId
			h.publishLocked()
		}
	}

	fun closeTab(sessionKey: String, tabId: String): Boolean {
		val h = holders[sessionKey] ?: return true
		synchronized(h) {
			val index = h.tabs.indexOfFirst { it.id == tabId }
			if (index < 0) return h.tabs.isEmpty()
			val removed = h.tabs.removeAt(index)
			removed.closed = true
			removed.connectGeneration++
			removed.isReconnecting = false
			removed.collectJob?.cancel()
			disconnectChannel(removed.channel)
			removed.channel = null
			h.maxReached = false
			if (h.tabs.isEmpty()) {
				h.scope.cancel()
				holders.remove(sessionKey)
				return true
			}
			if (h.activeId == tabId) {
				h.activeId = h.tabs[index.coerceAtMost(h.tabs.lastIndex)].id
			}
			h.publishLocked()
			return false
		}
	}

	fun closeForServer(serverId: Long) {
		val prefix = "${serverId}_"
		holders.keys.filter { it.startsWith(prefix) }.forEach { closeSession(it) }
	}

	fun closeSession(sessionKey: String) {
		val h = holders.remove(sessionKey) ?: return
		synchronized(h) {
			h.tabs.forEach { tab ->
				tab.closed = true
				tab.connectGeneration++
				tab.collectJob?.cancel()
				disconnectChannel(tab.channel)
			}
			h.tabs.clear()
			h.scope.cancel()
		}
	}

	fun activeTab(sessionKey: String): ShellTab? {
		val h = holders[sessionKey] ?: return null
		synchronized(h) {
			return h.tabs.firstOrNull { it.id == h.activeId } ?: h.tabs.firstOrNull()
		}
	}

	fun bindSession(sessionKey: String, session: Session) {
		val h = holders[sessionKey] ?: return
		synchronized(h) {
			h.session = session
		}
	}

	fun reconnectActiveShell(
		sessionKey: String,
		session: Session,
		terminalService: TerminalService
	) {
		val h = holders[sessionKey] ?: return
		val tab = synchronized(h) {
			h.session = session
			h.tabs.firstOrNull { it.id == h.activeId }
		} ?: return
		connectTab(h, tab, session, terminalService, force = true)
	}

	fun reconnectAllShells(
		sessionKey: String,
		session: Session,
		terminalService: TerminalService
	) {
		val h = holders[sessionKey] ?: return
		val tabs = synchronized(h) {
			h.session = session
			h.tabs.toList()
		}
		tabs.forEach { connectTab(h, it, session, terminalService, force = true) }
	}

	fun onPtySize(sessionKey: String, cols: Int, rows: Int) {
		val tab = activeTab(sessionKey) ?: return
		tab.ptyCols = cols
		tab.ptyRows = rows
		tab.buffer.setSize(cols, rows)
		tab.channel?.let { channel ->
			try {
				channel.setPtySize(cols, rows, 0, 0)
			} catch (_: Exception) {
			}
		}
		holders[sessionKey]?.let { h ->
			synchronized(h) { h.publishLocked() }
		}
	}

	fun clearMaxReached(sessionKey: String) {
		val h = holders[sessionKey] ?: return
		synchronized(h) {
			h.maxReached = false
			h.publishLocked()
		}
	}

	private val closeExecutor = Executors.newCachedThreadPool { runnable ->
		Thread(runnable, "shell-tab-close").apply { isDaemon = true }
	}

	private fun disconnectChannel(channel: ChannelShell?) {
		if (channel == null) return
		closeExecutor.execute {
			try {
				channel.disconnect()
			} catch (_: Exception) {
			}
		}
	}

	private fun holder(sessionKey: String): SessionShells {
		return holders.getOrPut(sessionKey) { SessionShells() }
	}

	private fun addTabLocked(
		h: SessionShells,
		session: Session,
		terminalService: TerminalService,
		cols: Int,
		rows: Int
	) {
		h.session = session
		val tab = ShellTab(
			id = UUID.randomUUID().toString(),
			titleIndex = h.nextTitleIndexLocked(),
			buffer = TerminalBuffer(screenRows = rows, cols = cols),
			ptyCols = cols,
			ptyRows = rows
		)
		h.tabs.add(tab)
		h.activeId = tab.id
		h.publishLocked()
		connectTab(h, tab, session, terminalService)
	}

	private fun connectTab(
		h: SessionShells,
		tab: ShellTab,
		session: Session,
		terminalService: TerminalService,
		force: Boolean = false
	) {
		val generation: Int
		synchronized(h) {
			if (tab.closed || !h.tabs.contains(tab)) return
			if (tab.isReconnecting && !force) return
			tab.connectGeneration++
			generation = tab.connectGeneration
			tab.isReconnecting = true
			tab.collectJob?.cancel()
			disconnectChannel(tab.channel)
			tab.channel = null
		}
		h.scope.launch {
			try {
				synchronized(h) {
					if (tab.closed || tab.connectGeneration != generation) return@launch
					tab.isConnecting = true
					tab.isConnected = false
					tab.error = null
					h.publishLocked()
				}
				val result = try {
					terminalService.connectShell(session, rows = tab.ptyRows, cols = tab.ptyCols)
				} catch (e: Exception) {
					Result.failure(e)
				}
				result.fold(
					onSuccess = { channel ->
						synchronized(h) {
							if (tab.closed || tab.connectGeneration != generation || !h.tabs.contains(tab)) {
								disconnectChannel(channel)
								return@fold
							}
							tab.channel = channel
							tab.isConnecting = false
							tab.isConnected = true
							tab.error = null
							h.publishLocked()
						}
						if (!tab.closed && tab.connectGeneration == generation) {
							collectOutput(h, tab, channel, session, terminalService, generation)
						} else {
							disconnectChannel(channel)
						}
					},
					onFailure = { exception ->
						synchronized(h) {
							if (tab.connectGeneration != generation) return@synchronized
							tab.isConnecting = false
							tab.isConnected = false
							if (!tab.closed && h.tabs.contains(tab)) {
								tab.error = exception.toAppError()
								h.publishLocked()
							}
						}
					}
				)
			} finally {
				synchronized(h) {
					if (tab.connectGeneration == generation) {
						tab.isReconnecting = false
						tab.isConnecting = false
						if (tab.channel == null) {
							tab.isConnected = false
						}
						h.publishLocked()
					}
				}
			}
		}
	}

	private fun collectOutput(
		h: SessionShells,
		tab: ShellTab,
		channel: ChannelShell,
		session: Session,
		terminalService: TerminalService,
		generation: Int
	) {
		tab.collectJob = h.scope.launch {
			try {
				terminalService.getOutputFlow(channel).collect { output ->
					if (tab.closed || tab.connectGeneration != generation) return@collect
					tab.buffer.writeBytes(output)
					synchronized(h) { h.publishLocked() }
				}
			} catch (_: Exception) {
			}
			synchronized(h) {
				if (tab.closed || tab.connectGeneration != generation || !h.tabs.contains(tab)) {
					return@launch
				}
				tab.isConnected = false
				tab.error = AppError.NetworkError("Shell connection closed")
				h.publishLocked()
			}
		}
	}

	private class SessionShells {
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		val tabs = mutableListOf<ShellTab>()
		var activeId: String = ""
		var maxReached: Boolean = false
		var session: Session? = null
		val snapshot = MutableStateFlow(ShellSessionSnapshot())

		fun nextTitleIndexLocked(): Int {
			val used = tabs.map { it.titleIndex }.toSet()
			return (1..MAX_TABS).first { it !in used }
		}

		fun publishLocked() {
			val active = tabs.firstOrNull { it.id == activeId } ?: tabs.firstOrNull()
			snapshot.value = ShellSessionSnapshot(
				tabs = tabs.map {
					ShellTabInfo(id = it.id, titleIndex = it.titleIndex, isActive = it.id == activeId)
				},
				activeId = active?.id,
				terminalBuffer = active?.buffer,
				output = active?.buffer?.getPlainText().orEmpty(),
				alternateScreen = active?.buffer?.isAlternateScreen() == true,
				frame = active?.buffer?.frame ?: 0,
				isConnecting = active?.isConnecting == true,
				isConnected = active?.isConnected == true,
				error = active?.error,
				maxReached = maxReached,
				canCreateTab = tabs.size < MAX_TABS
			)
		}
	}
}

class ShellTab(
	val id: String,
	val titleIndex: Int,
	var channel: ChannelShell? = null,
	val buffer: TerminalBuffer,
	var ptyCols: Int,
	var ptyRows: Int,
	var isConnecting: Boolean = false,
	var isConnected: Boolean = false,
	var error: AppError? = null,
	var collectJob: Job? = null,
	var isReconnecting: Boolean = false,
	var closed: Boolean = false,
	var connectGeneration: Int = 0,
	val inputMutex: Mutex = Mutex()
)

data class ShellTabInfo(
	val id: String,
	val titleIndex: Int,
	val isActive: Boolean
)

data class ShellSessionSnapshot(
	val tabs: List<ShellTabInfo> = emptyList(),
	val activeId: String? = null,
	val terminalBuffer: TerminalBuffer? = null,
	val output: String = "",
	val alternateScreen: Boolean = false,
	val frame: Int = 0,
	val isConnecting: Boolean = false,
	val isConnected: Boolean = false,
	val error: AppError? = null,
	val maxReached: Boolean = false,
	val canCreateTab: Boolean = true
)
