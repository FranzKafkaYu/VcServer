package com.franzkafkayu.vcserver.utils

enum class ShellReadAction {
	Emit,
	SkipEmpty,
	Closed
}

/**
 * 交互式 Shell 读循环的判定：空读继续等，EOF 才视为通道结束。
 * 写失败或短暂异常在通道仍打开时不得当成关闭。
 */
object ShellRead {
	fun classify(len: Int): ShellReadAction {
		return when {
			len < 0 -> ShellReadAction.Closed
			len == 0 -> ShellReadAction.SkipEmpty
			else -> ShellReadAction.Emit
		}
	}

	fun classifyException(channelOpen: Boolean): ShellReadAction {
		return if (channelOpen) ShellReadAction.SkipEmpty else ShellReadAction.Closed
	}
}
