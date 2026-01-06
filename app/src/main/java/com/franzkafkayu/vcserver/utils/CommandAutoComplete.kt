package com.franzkafkayu.vcserver.utils

/**
 * 命令自动补全工具
 */
object CommandAutoComplete {
	// 常见Linux命令列表
	private val commonCommands = listOf(
		"ls", "cd", "pwd", "cat", "grep", "find", "mkdir", "rm", "rmdir",
		"cp", "mv", "chmod", "chown", "ps", "top", "kill", "df", "du",
		"tar", "gzip", "gunzip", "unzip", "wget", "curl", "ssh", "scp",
		"vi", "vim", "nano", "less", "more", "head", "tail", "sort",
		"uniq", "wc", "cut", "sed", "awk", "echo", "printf", "date",
		"whoami", "id", "groups", "passwd", "su", "sudo", "exit", "logout",
		"history", "clear", "alias", "export", "env", "unset", "source",
		"which", "whereis", "locate", "updatedb", "man", "info", "help"
	)

	// 常见路径前缀
	private val commonPaths = listOf(
		"/usr", "/usr/local", "/usr/bin", "/usr/sbin",
		"/etc", "/var", "/tmp", "/home", "/root", "/opt"
	)

	/**
	 * 根据输入前缀查找匹配的命令
	 * @param prefix 命令前缀
	 * @return 匹配的命令列表（按匹配度排序）
	 */
	fun findMatchingCommands(prefix: String): List<String> {
		val trimmed = prefix.trim()
		if (trimmed.isEmpty()) return emptyList()

		return commonCommands.filter { it.startsWith(trimmed, ignoreCase = true) }
			.sorted()
	}

	/**
	 * 尝试补全命令（如果只有一个匹配项，返回完整命令；否则返回原输入）
	 * @param input 用户输入
	 * @return 补全后的命令，如果没有唯一匹配则返回原输入
	 */
	fun completeCommand(input: String): String {
		val trimmed = input.trim()
		if (trimmed.isEmpty()) return input

		// 检查是否是路径补全（例如：cd /u）
		val parts = trimmed.split(" ", limit = 2)
		if (parts.size == 2) {
			val command = parts[0]
			val pathPrefix = parts[1]

			// 如果是 cd 命令，尝试路径补全
			if (command == "cd" && pathPrefix.startsWith("/")) {
				val matchingPaths = commonPaths.filter { it.startsWith(pathPrefix) }
				if (matchingPaths.size == 1) {
					return "$command ${matchingPaths[0]}"
				}
			}
		}

		// 命令补全
		val matchingCommands = findMatchingCommands(trimmed)
		return when {
			matchingCommands.isEmpty() -> input
			matchingCommands.size == 1 -> matchingCommands[0]
			else -> {
				// 如果有多个匹配，返回最长的公共前缀
				val longestCommon = findLongestCommonPrefix(matchingCommands)
				if (longestCommon.length > trimmed.length) longestCommon else input
			}
		}
	}

	/**
	 * 查找最长公共前缀
	 */
	private fun findLongestCommonPrefix(strings: List<String>): String {
		if (strings.isEmpty()) return ""
		if (strings.size == 1) return strings[0]

		var prefix = strings[0]
		for (i in 1 until strings.size) {
			while (!strings[i].startsWith(prefix)) {
				prefix = prefix.substring(0, prefix.length - 1)
				if (prefix.isEmpty()) return ""
			}
		}
		return prefix
	}

	/**
	 * 获取所有匹配的命令（用于显示候选列表）
	 */
	fun getMatchingCommands(input: String): List<String> {
		return findMatchingCommands(input)
	}
}