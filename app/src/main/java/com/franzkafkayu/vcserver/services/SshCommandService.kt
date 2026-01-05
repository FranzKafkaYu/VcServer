package com.franzkafkayu.vcserver.services

import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * SSH 命令执行服务接口
 */
interface SshCommandService {
	/**
	 * 通过 SSH Session 执行命令
	 * @param session SSH 会话
	 * @param command 要执行的命令
	 * @param timeout 超时时间（毫秒），默认10s
	 * @return 命令执行结果（标准输出）
	 */
	suspend fun executeCommand(
		session: Session,
		command: String,
		timeout: Long = 10000
	): Result<String>
}

/**
 * SSH 命令执行服务实现
 * 
 * 优化说明：
 * - 使用 Mutex 替代 synchronized，只保护 Channel 的创建和连接阶段
 * - 允许多个命令并行执行（每个命令使用独立的 Channel）
 * - 读取操作不加锁，提高并发性能
 */
class SshCommandServiceImpl : SshCommandService {
	// 为每个 Session 维护一个 Mutex，确保 Channel 创建和连接的线程安全
	private val sessionMutexes = ConcurrentHashMap<Session, Mutex>()

	private fun getMutexForSession(session: Session): Mutex {
		return sessionMutexes.computeIfAbsent(session) { Mutex() }
	}

	override suspend fun executeCommand(
		session: Session,
		command: String,
		timeout: Long
	): Result<String> = withContext(Dispatchers.IO) {
		var execChannel: com.jcraft.jsch.ChannelExec? = null
		try {
			// 检查连接状态（不加锁，快速检查）
			if (!session.isConnected) {
				return@withContext Result.failure(
					Exception("SSH session is not connected")
				)
			}

			// 只对 Channel 的创建和连接加锁，这是唯一需要同步的部分
			// JSch 允许在同一个 Session 上创建多个独立的 Channel
			val mutex = getMutexForSession(session)
			val channel = mutex.withLock {
				if (!session.isConnected) {
					return@withContext Result.failure(
						Exception("SSH session is not connected")
					)
				}

				val ch = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
				ch.setCommand(command)
				ch.setInputStream(null)
				ch.setErrStream(null)
				ch.connect()
				ch
			}
			
			execChannel = channel

			// 锁释放后，读取操作可以并行执行
			// 每个 Channel 是独立的，读取操作不需要同步
			val inputStream: InputStream = channel.inputStream
			val output = StringBuilder()
			val buffer = ByteArray(1024)
			val startTime = System.currentTimeMillis()

			while (true) {
				if (System.currentTimeMillis() - startTime > timeout) {
					channel.disconnect()
					return@withContext Result.failure(
						Exception("Command execution timeout after ${timeout}ms")
					)
				}

				if (inputStream.available() > 0) {
					val len = inputStream.read(buffer, 0, buffer.size)
					if (len < 0) break
					output.append(String(buffer, 0, len))
				}

				if (channel.isClosed) {
					if (inputStream.available() > 0) continue
					break
				}

				Thread.sleep(100)
			}

			val exitStatus = channel.exitStatus
			channel.disconnect()

			if (exitStatus != 0) {
				return@withContext Result.failure(
					Exception("Command failed with exit status: $exitStatus")
				)
			}

			Result.success(output.toString().trim())
		} catch (e: Exception) {
			try {
				execChannel?.disconnect()
			} catch (ignored: Exception) {
				// 忽略断开连接时的错误
			}
			Result.failure(
				Exception("Failed to execute command: ${e.message}", e)
			)
		}
	}
}

