package com.franzkafkayu.vcserver.utils

import com.jcraft.jsch.Session
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 同一 Session 上 openChannel/connect 必须串行，且可被中断。
 */
object SshSessionLocks {
	private val locks = ConcurrentHashMap<Session, ReentrantLock>()

	fun lock(session: Session): ReentrantLock {
		return locks.computeIfAbsent(session) { ReentrantLock() }
	}

	fun <T> withLock(session: Session, block: () -> T): T {
		val lock = lock(session)
		lock.lockInterruptibly()
		try {
			return block()
		} finally {
			lock.unlock()
		}
	}

	fun remove(session: Session) {
		locks.remove(session)
	}
}
