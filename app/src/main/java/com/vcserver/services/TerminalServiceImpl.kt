package com.vcserver.services

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream
import java.io.OutputStream

/**
 * 终端服务实现
 */
class TerminalServiceImpl : TerminalService {
	override suspend fun connectShell(session: Session, rows: Int, cols: Int): Result<ChannelShell> = withContext(Dispatchers.IO) {
		try {
			if (!session.isConnected) {
				return@withContext Result.failure(
					Exception("SSH session is not connected")
				)
			}

			val channel = session.openChannel("shell") as ChannelShell
			// 启用伪终端，尽量模拟原生终端环境
			channel.setPty(true)
			channel.setPtyType("xterm-256color") // 支持256色
			// 设置终端窗口大小（对 top、htop 等程序很重要）
			channel.setPtySize(cols, rows, 0, 0) // width, height, widthPixels, heightPixels
			channel.connect()
			
			Result.success(channel)
		} catch (e: Exception) {
			Result.failure(
				Exception("Failed to connect shell: ${e.message}", e)
			)
		}
	}

	override suspend fun sendCommand(channel: ChannelShell, command: String) = withContext(Dispatchers.IO) {
		try {
			if (!channel.isConnected) {
				return@withContext
			}

			val outputStream: OutputStream = channel.outputStream
			val commandBytes = (command + "\n").toByteArray()
			outputStream.write(commandBytes)
			outputStream.flush()
		} catch (e: Exception) {
			throw Exception("Failed to send command: ${e.message}", e)
		}
	}

	override suspend fun sendRawBytes(channel: ChannelShell, bytes: ByteArray) = withContext(Dispatchers.IO) {
		try {
			if (!channel.isConnected) {
				return@withContext
			}

			val outputStream: OutputStream = channel.outputStream
			outputStream.write(bytes)
			outputStream.flush()
		} catch (e: Exception) {
			throw Exception("Failed to send raw bytes: ${e.message}", e)
		}
	}

	override suspend fun sendControlChar(channel: ChannelShell, controlChar: Int) = withContext(Dispatchers.IO) {
		try {
			if (!channel.isConnected) {
				return@withContext
			}

			val outputStream: OutputStream = channel.outputStream
			outputStream.write(controlChar)
			outputStream.flush()
		} catch (e: Exception) {
			throw Exception("Failed to send control char: ${e.message}", e)
		}
	}

	override fun getOutputFlow(channel: ChannelShell): Flow<String> = callbackFlow {
		val inputStream: InputStream = channel.inputStream
		val buffer = ByteArray(1024)
		var readerJob: Job? = null

		readerJob = CoroutineScope(Dispatchers.IO).launch {
			try {
				while (isActive && channel.isConnected) {
					if (inputStream.available() > 0) {
						val len = inputStream.read(buffer, 0, buffer.size)
						if (len > 0) {
							// 按 UTF-8 解码，避免中文等多字节字符乱码
							val output = String(buffer, 0, len, Charsets.UTF_8)
							trySend(output)
						} else if (len < 0) {
							break
						}
					} else {
						delay(50)
					}
				}
			} catch (e: Exception) {
				// 连接断开或其他错误
			} finally {
				close()
			}
		}

		awaitClose {
			readerJob?.cancel()
		}
	}

	override fun disconnectShell(channel: ChannelShell) {
		try {
			if (channel.isConnected) {
				channel.disconnect()
			}
		} catch (e: Exception) {
			// 忽略断开错误
		}
	}

	override fun isConnected(channel: ChannelShell): Boolean {
		return channel.isConnected
	}
}

