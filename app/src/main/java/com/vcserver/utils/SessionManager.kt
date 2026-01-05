package com.vcserver.utils

import com.jcraft.jsch.Session
import java.util.concurrent.ConcurrentHashMap

/**
 * Session 管理器，用于在导航之间传递 Session
 */
object SessionManager {
	private val sessions = ConcurrentHashMap<String, Session>()

	fun putSession(key: String, session: Session) {
		sessions[key] = session
	}

	fun getSession(key: String): Session? {
		return sessions[key]
	}

	fun removeSession(key: String) {
		sessions.remove(key)?.disconnect()
	}

	fun clear() {
		sessions.values.forEach { it.disconnect() }
		sessions.clear()
	}
}

