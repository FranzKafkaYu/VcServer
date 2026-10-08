package com.franzkafkayu.vcserver.utils

import androidx.compose.ui.graphics.Color

/**
 * ANSI / xterm 转义序列解析器
 * 支持 CSI SGR（含 256 色）、OSC 丢弃
 */
object AnsiParser {
	private const val ESC = '\u001B'
	private const val BEL = '\u0007'
	private const val CSI = "$ESC["
	private const val OSC = "$ESC]"

	val DefaultForeground = Color(0xFFE6E6E6)

	@Volatile
	private var pendingPrefix: String = ""

	fun parseAnsi(text: String): List<TextSegment> {
		val segments = mutableListOf<TextSegment>()
		var i = 0
		var currentColor: Color? = null
		var currentBgColor: Color? = null
		var isBold = false
		val currentText = StringBuilder()
		val data = pendingPrefix + text
		pendingPrefix = ""

		fun flush() {
			if (currentText.isNotEmpty()) {
				segments.add(
					TextSegment(
						text = currentText.toString(),
						color = currentColor,
						backgroundColor = currentBgColor,
						isBold = isBold
					)
				)
				currentText.clear()
			}
		}

		while (i < data.length) {
			val ch = data[i]
			if (ch == BEL) {
				i++
				continue
			}
			if (ch == ESC && i + 1 < data.length) {
				when {
					data.startsWith(CSI, i) -> {
						flush()
						var j = i + CSI.length
						while (j < data.length && (data[j] in '0'..'9' || data[j] == ';' || data[j] == '?')) {
							j++
						}
						if (j >= data.length) {
							pendingPrefix = data.substring(i)
							break
						}
						val command = data[j]
						val params = data.substring(i + CSI.length, j)
						val result = processAnsiCommand(command, params)
						if (result.resetAll) {
							currentColor = null
							currentBgColor = null
							isBold = false
						} else {
							result.color?.let { currentColor = it }
							result.backgroundColor?.let { currentBgColor = it }
							if (result.resetColor) currentColor = null
							if (result.resetBgColor) currentBgColor = null
							result.bold?.let { isBold = it }
						}
						i = j + 1
						continue
					}
					data.startsWith(OSC, i) -> {
						flush()
						val end = skipOsc(data, i)
						if (end < 0) {
							pendingPrefix = data.substring(i)
							break
						}
						i = end
						continue
					}
					else -> {
						flush()
						// ESC ( B 等字符集选择，勿把 "(B" 当可见字符
						if (i + 2 < data.length && data[i + 1] in "()*+") {
							i += 3
						} else {
							i++
						}
						continue
					}
				}
			}

			currentText.append(ch)
			i++
		}

		flush()
		return segments
	}

	fun stripAnsi(text: String): String {
		val result = StringBuilder()
		var i = 0
		val data = text
		while (i < data.length) {
			val ch = data[i]
			if (ch == BEL) {
				i++
				continue
			}
			if (ch == ESC && i + 1 < data.length) {
				when {
					data.startsWith(CSI, i) -> {
						var j = i + CSI.length
						while (j < data.length && (data[j].isDigit() || data[j] == ';' || data[j] == '?')) {
							j++
						}
						if (j < data.length) {
							i = j + 1
							continue
						}
						break
					}
					data.startsWith(OSC, i) -> {
						val end = skipOsc(data, i)
						if (end < 0) break
						i = end
						continue
					}
					else -> {
						if (i + 2 < data.length && data[i + 1] in "()*+") {
							i += 3
						} else {
							i++
						}
						continue
					}
				}
			}
			result.append(ch)
			i++
		}
		return result.toString()
	}

	private fun skipOsc(data: String, start: Int): Int {
		var j = start + OSC.length
		while (j < data.length) {
			when {
				data[j] == BEL -> return j + 1
				data[j] == ESC && j + 1 < data.length && data[j + 1] == '\\' -> return j + 2
				else -> j++
			}
		}
		return -1
	}

	private fun processAnsiCommand(command: Char, params: String): AnsiCommandResult {
		val result = AnsiCommandResult()
		when (command) {
			'm' -> {
				if (params.isEmpty()) {
					result.resetAll = true
				} else {
					val codes = params.split(';').map { it.toIntOrNull() ?: 0 }
					var idx = 0
					while (idx < codes.size) {
						when (val code = codes[idx]) {
							0 -> result.resetAll = true
							1 -> result.bold = true
							22 -> result.bold = false
							in 30..37 -> result.color = ansiColorToColor(code - 30)
							38, 48 -> {
								val isFg = code == 38
								if (idx + 2 < codes.size && codes[idx + 1] == 5) {
									val color = xterm256ToColor(codes[idx + 2])
									if (isFg) result.color = color else result.backgroundColor = color
									idx += 3
									continue
								}
								if (idx + 4 < codes.size && codes[idx + 1] == 2) {
									idx += 5
									continue
								}
							}
							39 -> result.resetColor = true
							in 40..47 -> result.backgroundColor = ansiColorToColor(code - 40)
							49 -> result.resetBgColor = true
							in 90..97 -> result.color = ansiBrightColorToColor(code - 90)
							in 100..107 -> result.backgroundColor = ansiBrightColorToColor(code - 100)
						}
						idx++
					}
				}
			}
			'J' -> result.clearScreen = true
			'K' -> result.clearLine = true
			'H', 'f' -> result.cursorHome = true
		}
		return result
	}

	fun xterm256ToColor(index: Int): Color {
		val n = index.coerceIn(0, 255)
		if (n < 8) return ansiColorToColor(n)
		if (n < 16) return ansiBrightColorToColor(n - 8)
		if (n < 232) {
			val c = n - 16
			val r = c / 36
			val g = (c % 36) / 6
			val b = c % 6
			fun level(v: Int) = if (v == 0) 0 else 55 + v * 40
			return rgb(level(r), level(g), level(b))
		}
		val gray = 8 + (n - 232) * 10
		return rgb(gray, gray, gray)
	}

	private fun rgb(r: Int, g: Int, b: Int): Color {
		return Color(
			red = r.coerceIn(0, 255) / 255f,
			green = g.coerceIn(0, 255) / 255f,
			blue = b.coerceIn(0, 255) / 255f
		)
	}

	private fun ansiColorToColor(index: Int): Color {
		return when (index) {
			0 -> Color(0xFF1E1E1E)
			1 -> Color(0xFFCD3131)
			2 -> Color(0xFF0DBC79)
			3 -> Color(0xFFE2C08D)
			4 -> Color(0xFF2472C8)
			5 -> Color(0xFFBC3FBC)
			6 -> Color(0xFF11A8CD)
			7 -> Color(0xFFE5E5E5)
			else -> DefaultForeground
		}
	}

	private fun ansiBrightColorToColor(index: Int): Color {
		return when (index) {
			0 -> Color(0xFF666666)
			1 -> Color(0xFFF14C4C)
			2 -> Color(0xFF23D18B)
			3 -> Color(0xFFF5F543)
			4 -> Color(0xFF3B8EEA)
			5 -> Color(0xFFD670D6)
			6 -> Color(0xFF29B8DB)
			7 -> Color(0xFFFFFFFF)
			else -> DefaultForeground
		}
	}
}

data class TextSegment(
	val text: String,
	val color: Color? = null,
	val backgroundColor: Color? = null,
	val isBold: Boolean = false
)

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
