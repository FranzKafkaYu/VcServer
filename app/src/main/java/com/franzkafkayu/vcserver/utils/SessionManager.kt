package com.franzkafkayu.vcserver.utils

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
		// 只从 SessionManager 移除，不断开连接
		// 连接由连接池管理，保留在连接池中供后续复用
		sessions.remove(key)
	}

	fun clear() {
		sessions.values.forEach { it.disconnect() }
		sessions.clear()
	}
}