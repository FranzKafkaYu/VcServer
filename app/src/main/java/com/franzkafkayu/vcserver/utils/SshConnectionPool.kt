package com.franzkafkayu.vcserver.utils

import android.util.Log
import com.jcraft.jsch.Session
import java.util.concurrent.ConcurrentHashMap

/**
 * SSH 连接池，用于复用 SSH Session 连接
 * 
 * 职责：
 * - 管理 SSH Session 的生命周期
 * - 支持连接复用，减少连接建立时间
 * - 自动清理无效和超时的连接
 */
object SshConnectionPool {
	private const val TAG = "SshConnectionPool"
	
	// 连接最大空闲时间（5 分钟）
	private const val MAX_IDLE_TIME_MS = 5 * 60 * 1000L
	
	/**
	 * 连接包装类，包含 Session 和最后使用时间
	 */
	private data class PooledConnection(
		val session: Session,
		var lastUsedTime: Long = System.currentTimeMillis()
	) {
		fun updateLastUsedTime() {
			lastUsedTime = System.currentTimeMillis()
		}
		
		fun isIdle(): Boolean {
			return System.currentTimeMillis() - lastUsedTime > MAX_IDLE_TIME_MS
		}
		
		fun isConnected(): Boolean {
			return session.isConnected
		}
	}
	
	// 连接池：key 为 serverId，value 为 PooledConnection
	private val connectionPool = ConcurrentHashMap<Long, PooledConnection>()
	
	/**
	 * 获取连接（如果存在且有效）
	 * @param serverId 服务器 ID
	 * @return 有效的 Session，如果不存在或无效则返回 null
	 */
	fun getConnection(serverId: Long): Session? {
		val pooledConnection = connectionPool[serverId] ?: return null
		
		// 检查连接是否有效
		if (!pooledConnection.isConnected()) {
			Log.d(TAG, "连接已断开，从连接池移除: serverId=$serverId")
			removeConnection(serverId)
			return null
		}
		
		// 检查连接是否超时
		if (pooledConnection.isIdle()) {
			Log.d(TAG, "连接空闲超时，从连接池移除: serverId=$serverId")
			removeConnection(serverId)
			return null
		}
		
		// 更新最后使用时间
		pooledConnection.updateLastUsedTime()
		Log.d(TAG, "复用连接: serverId=$serverId")
		return pooledConnection.session
	}
	
	/**
	 * 添加连接到连接池
	 * @param serverId 服务器 ID
	 * @param session SSH Session
	 */
	fun putConnection(serverId: Long, session: Session) {
		if (!session.isConnected) {
			Log.w(TAG, "尝试添加未连接的 Session 到连接池: serverId=$serverId")
			return
		}
		
		connectionPool[serverId] = PooledConnection(session)
		Log.d(TAG, "添加连接到连接池: serverId=$serverId")
	}
	
	/**
	 * 从连接池移除连接并断开
	 * @param serverId 服务器 ID
	 */
	fun removeConnection(serverId: Long) {
		val pooledConnection = connectionPool.remove(serverId)
		pooledConnection?.let {
			try {
				if (it.session.isConnected) {
					it.session.disconnect()
					Log.d(TAG, "断开并移除连接: serverId=$serverId")
				} else {
					Log.d(TAG, "连接已断开，从连接池移除: serverId=$serverId")
				}
			} catch (e: Exception) {
				Log.e(TAG, "断开连接时出错: serverId=$serverId", e)
			}
		}
	}
	
	/**
	 * 清理所有连接
	 */
	fun clear() {
		Log.d(TAG, "清理所有连接，共 ${connectionPool.size} 个")
		connectionPool.keys.toList().forEach { serverId ->
			removeConnection(serverId)
		}
		connectionPool.clear()
	}
	
	/**
	 * 清理空闲超时的连接
	 */
	fun cleanupIdleConnections() {
		val now = System.currentTimeMillis()
		val idleConnections = connectionPool.filter { (_, pooled) ->
			pooled.isIdle() || !pooled.isConnected()
		}
		
		if (idleConnections.isNotEmpty()) {
			Log.d(TAG, "清理 ${idleConnections.size} 个空闲或无效连接")
			idleConnections.keys.forEach { serverId ->
				removeConnection(serverId)
			}
		}
	}
	
	/**
	 * 获取连接池统计信息（用于调试）
	 */
	fun getStats(): Map<String, Any> {
		val now = System.currentTimeMillis()
		val activeConnections = connectionPool.values.count { 
			it.isConnected() && !it.isIdle() 
		}
		val idleConnections = connectionPool.values.count { it.isIdle() }
		val disconnectedConnections = connectionPool.values.count { !it.isConnected() }
		
		return mapOf(
			"total" to connectionPool.size,
			"active" to activeConnections,
			"idle" to idleConnections,
			"disconnected" to disconnectedConnections
		)
	}
}
