package com.vcserver.services

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import kotlinx.coroutines.flow.Flow

/**
 * 终端服务接口
 */
interface TerminalService {
	/**
	 * 连接到Shell通道
	 * @param session SSH会话
	 * @param rows 终端行数（默认80）
	 * @param cols 终端列数（默认24）
	 * @return 连接结果
	 */
	suspend fun connectShell(session: Session, rows: Int = 24, cols: Int = 80): Result<ChannelShell>

	/**
	 * 发送命令到Shell
	 * @param channel Shell通道
	 * @param command 命令字符串
	 */
	suspend fun sendCommand(channel: ChannelShell, command: String)

	/**
	 * 发送原始字节到Shell（用于控制字符和实时输入）
	 * @param channel Shell通道
	 * @param bytes 要发送的字节数组
	 */
	suspend fun sendRawBytes(channel: ChannelShell, bytes: ByteArray)

	/**
	 * 发送控制字符到Shell
	 * @param channel Shell通道
	 * @param controlChar 控制字符（例如：Ctrl+C = 0x03, Ctrl+D = 0x04）
	 */
	suspend fun sendControlChar(channel: ChannelShell, controlChar: Int)

	/**
	 * 获取Shell输出流
	 * @param channel Shell通道
	 * @return 输出流（Flow<String>）
	 */
	fun getOutputFlow(channel: ChannelShell): Flow<String>

	/**
	 * 断开Shell通道
	 * @param channel Shell通道
	 */
	fun disconnectShell(channel: ChannelShell)

	/**
	 * 检查Shell通道是否连接
	 * @param channel Shell通道
	 * @return 是否连接
	 */
	fun isConnected(channel: ChannelShell): Boolean
}

