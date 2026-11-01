package com.franzkafkayu.vcserver.utils

/**
 * 命令模式发给 PTY 的字节。交互式终端的 Enter 是 CR，不是 LF 或 EOF。
 */
object ShellInput {
	const val ENTER: Byte = 0x0D
	const val EOF: Byte = 0x04

	fun commandLine(command: String): ByteArray? {
		val trimmed = command.trim()
		if (trimmed.isEmpty()) return null
		val text = trimmed.toByteArray(Charsets.UTF_8)
		return text + byteArrayOf(ENTER)
	}
}
