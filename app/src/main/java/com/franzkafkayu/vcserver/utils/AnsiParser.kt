package com.franzkafkayu.vcserver.utils

import androidx.compose.ui.graphics.Color

/**
 * ANSI / xterm 转义序列解析器
 * 支持 CSI SGR（含 256 色）、OSC 丢弃
 */
object AnsiParser {
	internal const val ESC = '\u001B'
	internal const val BEL = '\u0007'
	internal const val CSI = "$ESC["
	internal const val OSC = "$ESC]"

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

	internal fun skipOsc(data: String, start: Int): Int {
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

	internal fun ansiColorToColor(index: Int): Color {
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

	internal fun ansiBrightColorToColor(index: Int): Color {
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

internal sealed class AnsiAction {
	data class Print(val text: String) : AnsiAction()
	data class Style(val fg: Color?, val bg: Color?, val bold: Boolean) : AnsiAction()
	data class Cup(val row: Int, val col: Int) : AnsiAction()
	data class Move(val command: Char, val count: Int) : AnsiAction()
	data class EraseDisplay(val mode: Int) : AnsiAction()
	data class EraseLine(val mode: Int) : AnsiAction()
	data class EraseChars(val count: Int) : AnsiAction()
	data class ScrollRegion(val top: Int, val bottom: Int) : AnsiAction()
	data class InsertLines(val count: Int) : AnsiAction()
	data class DeleteLines(val count: Int) : AnsiAction()
	data class CursorMark(val restore: Boolean) : AnsiAction()
	data class AltScreen(val enter: Boolean) : AnsiAction()
	data class DecCkm(val application: Boolean) : AnsiAction()
	data object Index : AnsiAction()
	data object ReverseIndex : AnsiAction()
}

/**
 * 按到达顺序产出文本和屏幕操作。不完整的 ESC 留到下一次 [feed]。
 */
internal class AnsiStream {
	private var pending = ""
	private var fg: Color? = null
	private var bg: Color? = null
	private var bold = false

	fun feed(chunk: String): List<AnsiAction> {
		val data = pending + chunk
		pending = ""
		val actions = mutableListOf<AnsiAction>()
		val text = StringBuilder()
		var i = 0

		fun flush() {
			if (text.isNotEmpty()) {
				actions.add(AnsiAction.Print(text.toString()))
				text.clear()
			}
		}

		while (i < data.length) {
			val ch = data[i]
			if (ch == AnsiParser.BEL) {
				i++
				continue
			}
			if (ch == AnsiParser.ESC) {
				if (i + 1 >= data.length) {
					pending = data.substring(i)
					break
				}
				flush()
				when {
					data.startsWith(AnsiParser.CSI, i) -> {
						var j = i + AnsiParser.CSI.length
						while (j < data.length && (data[j] in '0'..'9' || data[j] == ';' || data[j] == '?')) {
							j++
						}
						if (j >= data.length) {
							pending = data.substring(i)
							break
						}
						val command = data[j]
						val params = data.substring(i + AnsiParser.CSI.length, j)
						actions.addAll(csi(command, params))
						i = j + 1
					}
					data.startsWith(AnsiParser.OSC, i) -> {
						val end = AnsiParser.skipOsc(data, i)
						if (end < 0) {
							pending = data.substring(i)
							break
						}
						i = end
					}
					else -> {
						val next = data[i + 1]
						when (next) {
							'7' -> {
								actions.add(AnsiAction.CursorMark(restore = false))
								i += 2
							}
							'8' -> {
								actions.add(AnsiAction.CursorMark(restore = true))
								i += 2
							}
							'D' -> {
								actions.add(AnsiAction.Index)
								i += 2
							}
							'M' -> {
								actions.add(AnsiAction.ReverseIndex)
								i += 2
							}
							in "()*+" -> {
								if (i + 2 >= data.length) {
									pending = data.substring(i)
									break
								}
								i += 3
							}
							else -> i += 2
						}
					}
				}
				continue
			}
			text.append(ch)
			i++
		}
		flush()
		return actions
	}

	private fun csi(command: Char, params: String): List<AnsiAction> {
		fun count(default: Int = 1): Int {
			val n = params.substringBefore(';').removePrefix("?").toIntOrNull()
			return if (n == null || n == 0) default else n
		}
		fun cupPart(index: Int): Int {
			if (params.isEmpty()) return 1
			val raw = params.split(';').getOrNull(index)?.removePrefix("?")?.toIntOrNull()
			return if (raw == null || raw == 0) 1 else raw
		}
		return when (command) {
			'm' -> {
				applySgr(params)
				listOf(AnsiAction.Style(fg, bg, bold))
			}
			'H', 'f' -> listOf(AnsiAction.Cup(cupPart(0), cupPart(1)))
			'A', 'B', 'C', 'D' -> listOf(AnsiAction.Move(command, count()))
			'G' -> listOf(AnsiAction.Cup(0, cupPart(0)))
			'd' -> listOf(AnsiAction.Cup(cupPart(0), 0))
			'J' -> listOf(AnsiAction.EraseDisplay(params.toIntOrNull() ?: 0))
			'K' -> listOf(AnsiAction.EraseLine(params.toIntOrNull() ?: 0))
			'X' -> listOf(AnsiAction.EraseChars(count()))
			'r' -> {
				if (params.isEmpty()) {
					listOf(AnsiAction.ScrollRegion(0, 0))
				} else {
					listOf(AnsiAction.ScrollRegion(cupPart(0), cupPart(1)))
				}
			}
			'L' -> listOf(AnsiAction.InsertLines(count()))
			'M' -> listOf(AnsiAction.DeleteLines(count()))
			's' -> listOf(AnsiAction.CursorMark(restore = false))
			'u' -> listOf(AnsiAction.CursorMark(restore = true))
			'h', 'l' -> mode(command == 'h', params)
			else -> emptyList()
		}
	}

	private fun mode(on: Boolean, params: String): List<AnsiAction> {
		val actions = mutableListOf<AnsiAction>()
		for (part in params.split(';')) {
			when (part.removePrefix("?")) {
				"1049", "47", "1047" -> if (part.startsWith("?")) {
					actions.add(AnsiAction.AltScreen(on))
				}
				"1" -> if (part.startsWith("?")) {
					actions.add(AnsiAction.DecCkm(on))
				}
			}
		}
		return actions
	}

	private fun applySgr(params: String) {
		if (params.isEmpty()) {
			fg = null
			bg = null
			bold = false
			return
		}
		val codes = params.split(';').map { it.toIntOrNull() ?: 0 }
		var idx = 0
		while (idx < codes.size) {
			when (val code = codes[idx]) {
				0 -> {
					fg = null
					bg = null
					bold = false
				}
				1 -> bold = true
				22 -> bold = false
				in 30..37 -> fg = AnsiParser.ansiColorToColor(code - 30)
				38, 48 -> {
					val foreground = code == 38
					if (idx + 2 < codes.size && codes[idx + 1] == 5) {
						val color = AnsiParser.xterm256ToColor(codes[idx + 2])
						if (foreground) fg = color else bg = color
						idx += 3
						continue
					}
					if (idx + 4 < codes.size && codes[idx + 1] == 2) {
						idx += 5
						continue
					}
				}
				39 -> fg = null
				in 40..47 -> bg = AnsiParser.ansiColorToColor(code - 40)
				49 -> bg = null
				in 90..97 -> fg = AnsiParser.ansiBrightColorToColor(code - 90)
				in 100..107 -> bg = AnsiParser.ansiBrightColorToColor(code - 100)
			}
			idx++
		}
	}
}
