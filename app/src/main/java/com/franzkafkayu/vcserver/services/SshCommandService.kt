package com.franzkafkayu.vcserver.services

import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * SSH 命令执行服务接口
 */
interface SshCommandService {
	/**
	 * 通过 SSH Session 执行命令
	 * @param session SSH 会话
	 * @param command 要执行的命令
	 * @param timeout 超时时间（毫秒），默�?10 �?
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
 */
class SshCommandServiceImpl : SshCommandService {
	override suspend fun executeCommand(
		session: Session,
		command: String,
		timeout: Long
	): Result<String> = withContext(Dispatchers.IO) {
		try {
			if (!session.isConnected) {
				return@withContext Result.failure(
					Exception("SSH session is not connected")
				)
			}

			val channel = session.openChannel("exec")
			val execChannel = channel as com.jcraft.jsch.ChannelExec
			execChannel.setCommand(command)
			execChannel.setInputStream(null)
			execChannel.setErrStream(null)

			val inputStream: InputStream = execChannel.inputStream
			execChannel.connect()

			// 读取命令输出
			val output = StringBuilder()
			val buffer = ByteArray(1024)
			var startTime = System.currentTimeMillis()

			while (true) {
				if (System.currentTimeMillis() - startTime > timeout) {
					execChannel.disconnect()
					return@withContext Result.failure(
						Exception("Command execution timeout after ${timeout}ms")
					)
				}

				if (inputStream.available() > 0) {
					val len = inputStream.read(buffer, 0, buffer.size)
					if (len < 0) break
					output.append(String(buffer, 0, len))
				}

				if (execChannel.isClosed) {
					if (inputStream.available() > 0) continue
					break
				}

				Thread.sleep(100)
			}

			val exitStatus = execChannel.exitStatus
			execChannel.disconnect()

			if (exitStatus != 0) {
				return@withContext Result.failure(
					Exception("Command failed with exit status: $exitStatus")
				)
			}

			Result.success(output.toString().trim())
		} catch (e: Exception) {
			Result.failure(
				Exception("Failed to execute command: ${e.message}", e)
			)
		}
	}
}

