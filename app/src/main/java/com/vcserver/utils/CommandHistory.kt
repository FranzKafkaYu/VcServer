package com.vcserver.utils

/**
 * 命令历史管理器（仅在当前会话内有效）
 */
class CommandHistory(private val maxSize: Int = 100) {
	private val history = mutableListOf<String>()
	private var currentIndex = -1

	/**
	 * 添加命令到历史记录
	 */
	fun addCommand(command: String) {
		val trimmed = command.trim()
		if (trimmed.isNotEmpty()) {
			// 如果与最后一条命令相同，不重复添加
			if (history.isEmpty() || history.last() != trimmed) {
				history.add(trimmed)
				// 限制历史记录大小
				if (history.size > maxSize) {
					history.removeAt(0)
				}
			}
			currentIndex = history.size
		}
	}

	/**
	 * 获取上一条命令
	 */
	fun getPreviousCommand(): String? {
		if (history.isEmpty()) return null
		if (currentIndex > 0) {
			currentIndex--
		}
		return if (currentIndex >= 0 && currentIndex < history.size) {
			history[currentIndex]
		} else null
	}

	/**
	 * 获取下一条命令
	 */
	fun getNextCommand(): String? {
		if (history.isEmpty()) return null
		if (currentIndex < history.size - 1) {
			currentIndex++
			return history[currentIndex]
		} else {
			currentIndex = history.size
			return ""
		}
	}

	/**
	 * 重置索引（用于开始新的命令输入）
	 */
	fun resetIndex() {
		currentIndex = history.size
	}

	/**
	 * 清空历史记录
	 */
	fun clear() {
		history.clear()
		currentIndex = -1
	}

	/**
	 * 获取所有历史记录（用于调试）
	 */
	fun getAllHistory(): List<String> = history.toList()
}

