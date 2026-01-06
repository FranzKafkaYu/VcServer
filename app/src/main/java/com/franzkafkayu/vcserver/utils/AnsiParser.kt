package com.franzkafkayu.vcserver.utils

import androidx.compose.ui.graphics.Color


/**
 * ANSI 转义序列解析器
 * 支持基本的 VT100/ANSI 序列：颜色、光标移动、清屏等
 */
object AnsiParser {
	// ANSI 转义序列开始标记
	private const val ESC = '\u001B'
	private const val CSI = "$ESC["

	/**
	 * 解析 ANSI 转义序列，返回格式化的文本片段列表
	 */
	fun parseAnsi(text: String): List<TextSegment> {
		val segments = mutableListOf<TextSegment>()
		var i = 0
		var currentColor: Color? = null
		var currentBgColor: Color? = null
		var isBold = false
		var currentText = StringBuilder()

		while (i < text.length) {
			if (text[i] == ESC && i + 1 < text.length) {
				// 处理转义序列
				when {
					// CSI 序列 (ESC[)
					text.startsWith(CSI, i) -> {
						// 先保存当前文本
						if (currentText.isNotEmpty()) {
							segments.add(TextSegment(
								text = currentText.toString(),
								color = currentColor,
								backgroundColor = currentBgColor,
								isBold = isBold
							))
							currentText.clear()
						}

						// 查找序列结束位置
						var j = i + CSI.length
						while (j < text.length && (text[j] in '0'..'9' || text[j] == ';' || text[j] == '?')) {
							j++
						}
						if (j < text.length) {
							val command = text[j]
							val params = text.substring(i + CSI.length, j)
							val result = processAnsiCommand(command, params)

							result.color?.let { currentColor = it }
							result.backgroundColor?.let { currentBgColor = it }
							if (result.resetColor) currentColor = null
							if (result.resetBgColor) currentBgColor = null
							if (result.resetAll) {
								currentColor = null
								currentBgColor = null
								isBold = false
							}
							result.bold?.let { isBold = it }

							i = j + 1
							continue
						}
					}
					// 其他转义序列（如 ESC] OSC 序列等）
					else -> {
						// 跳过未知的转义序列
						i++
						continue
					}
				}
			}

			currentText.append(text[i])
			i++
		}

		// 添加剩余的文本
		if (currentText.isNotEmpty()) {
			segments.add(TextSegment(
				text = currentText.toString(),
				color = currentColor,
				backgroundColor = currentBgColor,
				isBold = isBold
			))
		}

		return segments
	}

	/**
	 * 处理 ANSI 命令
	 */
	private fun processAnsiCommand(command: Char, params: String): AnsiCommandResult {
		val result = AnsiCommandResult()

		when (command) {
			'm' -> {
				// SGR (Select Graphic Rendition)
				if (params.isEmpty()) {
					result.resetAll = true
				} else {
					val codes = params.split(';').mapNotNull { it.toIntOrNull() }
					for (code in codes) {
						when (code) {
							0 -> result.resetAll = true
							1 -> result.bold = true
							22 -> result.bold = false
							30, 31, 32, 33, 34, 35, 36, 37 -> result.color = ansiColorToColor(code - 30)
							38 -> {
								// 38;5;n (256色) 或 38;2;r;g;b (RGB)
								// 简化处理：跳过
							}
							39 -> result.resetColor = true
							40, 41, 42, 43, 44, 45, 46, 47 -> result.backgroundColor = ansiColorToColor(code - 40)
							48 -> {
								// 48;5;n 或 48;2;r;g;b
								// 简化处理：跳过
							}
							49 -> result.resetBgColor = true
							90, 91, 92, 93, 94, 95, 96, 97 -> result.color = ansiBrightColorToColor(code - 90)
							100, 101, 102, 103, 104, 105, 106, 107 -> result.backgroundColor = ansiBrightColorToColor(code - 100)
						}
					}
				}
			}
			'J' -> {
				// 清屏命令（简化处理：标记需要清屏）
				result.clearScreen = true
			}
			'K' -> {
				// 清除行（简化处理）
				result.clearLine = true
			}
			'H', 'f' -> {
				// 光标定位（简化处理：标记需要重置光标）
				result.cursorHome = true
			}
		}

		return result
	}

	/**
	 * ANSI 标准颜色转 Compose Color
	 */
	private fun ansiColorToColor(index: Int): Color {
		return when (index) {
			0 -> Color(0xFF000000) // 黑色
			1 -> Color(0xFF800000) // 红色
			2 -> Color(0xFF008000) // 绿色
			3 -> Color(0xFF808000) // 黄色
			4 -> Color(0xFF000080) // 蓝色
			5 -> Color(0xFF800080) // 洋红
			6 -> Color(0xFF008080) // 青色
			7 -> Color(0xFFC0C0C0) // 白色
			else -> Color(0xFF00FF00) // 默认绿色
		}
	}

	/**
	 * ANSI 高亮颜色转 Compose Color
	 */
	private fun ansiBrightColorToColor(index: Int): Color {
		return when (index) {
			0 -> Color(0xFF808080) // 灰色
			1 -> Color(0xFFFF0000) // 亮红
			2 -> Color(0xFF00FF00) // 亮绿
			3 -> Color(0xFFFFFF00) // 亮黄
			4 -> Color(0xFF0000FF) // 亮蓝
			5 -> Color(0xFFFF00FF) // 亮洋红
			6 -> Color(0xFF00FFFF) // 亮青
			7 -> Color(0xFFFFFFFF) // 亮白
			else -> Color(0xFF00FF00)
		}
	}

	/**
	 * 移除 ANSI 转义序列，返回纯文本
	 */
	fun stripAnsi(text: String): String {
		val result = StringBuilder()
		var i = 0

		while (i < text.length) {
			if (text[i] == ESC && i + 1 < text.length) {
				if (text.startsWith(CSI, i)) {
					var j = i + CSI.length
					while (j < text.length && (text[j].isDigit() || text[j] == ';' || text[j] == '?')) {
						j++
					}
					if (j < text.length) {
						i = j + 1
						continue
					}
				} else {
					i++
					continue
				}
			}
			result.append(text[i])
			i++
		}

		return result.toString()
	}
}

/**
 * 文本片段（带格式信息）
 */
data class TextSegment(
	val text: String,
	val color: Color? = null,
	val backgroundColor: Color? = null,
	val isBold: Boolean = false
)

/**
 * ANSI 命令处理结果
 */
private data class AnsiCommandResult(
	var color: Color? = null,
	var backgroundColor: Color? = null,
	var resetColor: Boolean = false,
	var resetBgColor: Boolean = false,
	var resetAll: Boolean = false,
	var bold: Boolean? = null,
	var clearScreen: Boolean = false,
	var clearLine: Boolean = false,
	var cursorHome: Boolean = false
)

