package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.ssh.SshClientSession
import com.franzkafkayu.vcserver.ssh.SshShellChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 终端服务实现
 */
class TerminalServiceImpl : TerminalService {
	override suspend fun connectShell(
		session: SshClientSession,
		rows: Int,
		cols: Int
	): Result<SshShellChannel> = withContext(Dispatchers.IO) {
		if (!session.isConnected) {
			return@withContext Result.failure(Exception("SSH session is not connected"))
		}
		val executor = Executors.newSingleThreadExecutor()
		val future = executor.submit<SshShellChannel> {
			if (!session.isConnected) {
				throw Exception("SSH session is not connected")
			}
			session.openShell(cols, rows)
		}
		try {
			Result.success(future.get(12, TimeUnit.SECONDS))
		} catch (e: TimeoutException) {
			future.cancel(true)
			Result.failure(Exception("Shell connect timeout"))
		} catch (e: Exception) {
			future.cancel(true)
			val cause = e.cause ?: e
			Result.failure(Exception("Failed to connect shell: ${cause.message}", cause))
		} finally {
			executor.shutdownNow()
		}
	}

	override suspend fun sendCommand(channel: SshShellChannel, command: String) = withContext(Dispatchers.IO) {
		write(channel, (command + "\n").toByteArray())
	}

	override suspend fun sendRawBytes(channel: SshShellChannel, bytes: ByteArray) = withContext(Dispatchers.IO) {
		write(channel, bytes)
	}

	override suspend fun sendControlChar(channel: SshShellChannel, controlChar: Int) = withContext(Dispatchers.IO) {
		write(channel, byteArrayOf(controlChar.toByte()))
	}

	private fun write(channel: SshShellChannel, bytes: ByteArray) {
		try {
			if (!channel.isOpen()) {
				return
			}
			val outputStream: OutputStream = channel.outputStream
			outputStream.write(bytes)
			outputStream.flush()
		} catch (e: Exception) {
			throw Exception("Failed to write shell: ${e.message}", e)
		}
	}

	override fun getOutputFlow(channel: SshShellChannel): Flow<ByteArray> = callbackFlow {
		val inputStream = channel.inputStream
		val buffer = ByteArray(4096)
		var readerJob: Job? = null

		readerJob = CoroutineScope(Dispatchers.IO).launch {
			try {
				while (isActive && channel.isOpen()) {
					val len = inputStream.read(buffer)
					if (len < 0) break
					if (len > 0) {
						trySend(buffer.copyOf(len))
					}
				}
			} catch (_: Exception) {
			} finally {
				close()
			}
		}

		awaitClose {
			readerJob?.cancel()
		}
	}

	override fun disconnectShell(channel: SshShellChannel) {
		channel.close()
	}

	override fun isConnected(channel: SshShellChannel): Boolean = channel.isOpen()
}
