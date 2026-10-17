package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.ssh.SshClientSession
import com.franzkafkayu.vcserver.ssh.SshShellChannel
import kotlinx.coroutines.flow.Flow

/**
 * 终端服务接口
 */
interface TerminalService {
	/**
	 * 连接到 Shell 通道
	 */
	suspend fun connectShell(session: SshClientSession, rows: Int = 24, cols: Int = 80): Result<SshShellChannel>

	suspend fun sendCommand(channel: SshShellChannel, command: String)

	suspend fun sendRawBytes(channel: SshShellChannel, bytes: ByteArray)

	suspend fun sendControlChar(channel: SshShellChannel, controlChar: Int)

	fun getOutputFlow(channel: SshShellChannel): Flow<ByteArray>

	fun disconnectShell(channel: SshShellChannel)

	fun isConnected(channel: SshShellChannel): Boolean
}
