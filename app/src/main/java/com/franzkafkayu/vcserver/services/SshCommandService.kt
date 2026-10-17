package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.ssh.SshClientSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SSH 命令执行服务接口
 */
interface SshCommandService {
	/**
	 * 通过已认证连接执行命令
	 * @param session SSH 会话
	 * @param command 要执行的命令
	 * @param timeout 超时时间（毫秒），默认 10s
	 * @return 命令执行结果（标准输出）
	 */
	suspend fun executeCommand(
		session: SshClientSession,
		command: String,
		timeout: Long = 10000
	): Result<String>
}

/**
 * 同一连接上的 exec 各自独立，不再串行化开通道。
 */
class SshCommandServiceImpl : SshCommandService {
	override suspend fun executeCommand(
		session: SshClientSession,
		command: String,
		timeout: Long
	): Result<String> = withContext(Dispatchers.IO) {
		try {
			if (!session.isConnected) {
				return@withContext Result.failure(Exception("SSH session is not connected"))
			}
			Result.success(session.exec(command, timeout))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to execute command: ${e.message}", e))
		}
	}
}
