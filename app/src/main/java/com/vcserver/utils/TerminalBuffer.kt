package com.vcserver.utils

import androidx.compose.ui.graphics.Color

/**
 * 终端缓冲区
 * 维护一个二维字符网格，支持 ANSI 转义序列操作
 */
class TerminalBuffer(
	private val rows: Int = 1000, // 最大行数
	private val cols: Int = 120   // 每行最大列数
) {
	private val buffer = mutableListOf<MutableList<CharCell>>()
	private var cursorRow = 0
	private var cursorCol = 0
	private var currentFgColor: Color? = null
	private var currentBgColor: Color? = null
	private var isBold = false

	init {
		// 初始化缓冲区
		for (i in 0 until rows) {
			buffer.add(mutableListOf())
		}
	}

	/**
	 * 写入文本（处理 ANSI 转义序列）
	 */
	fun write(text: String) {
		var i = 0
		val segments = AnsiParser.parseAnsi(text)

		for (segment in segments) {
			// 更新当前格式
			segment.color?.let { currentFgColor = it }
			segment.backgroundColor?.let { currentBgColor = it }
			if (segment.isBold) isBold = true

			// 写入文本
			for (char in segment.text) {
				when (char) {
					'\n' -> {
						newLine()
					}
					'\r' -> {
						cursorCol = 0
					}
					'\t' -> {
						// Tab 转换为空格
						repeat(4) { writeChar(' ') }
					}
					else -> {
						writeChar(char)
					}
				}
			}
		}
	}

	/**
	 * 写入单个字符
	 */
	private fun writeChar(char: Char) {
		ensureRow(cursorRow)
		val row = buffer[cursorRow]

		// 确保列足够
		while (row.size <= cursorCol) {
			row.add(CharCell(' ', null, null, false))
		}

		// 写入字符
		if (cursorCol < row.size) {
			row[cursorCol] = CharCell(char, currentFgColor, currentBgColor, isBold)
		} else {
			row.add(CharCell(char, currentFgColor, currentBgColor, isBold))
		}

		cursorCol++
		if (cursorCol >= cols) {
			newLine()
		}
	}

	/**
	 * 换行
	 */
	private fun newLine() {
		cursorRow++
		cursorCol = 0
		if (cursorRow >= rows) {
			// 滚动：移除第一行，添加新行
			buffer.removeAt(0)
			buffer.add(mutableListOf())
			cursorRow = rows - 1
		}
		ensureRow(cursorRow)
	}

	/**
	 * 确保行存在
	 */
	private fun ensureRow(row: Int) {
		while (buffer.size <= row) {
			buffer.add(mutableListOf())
		}
	}

	/**
	 * 处理 ANSI 清屏命令
	 */
	fun clearScreen() {
		buffer.clear()
		for (i in 0 until rows) {
			buffer.add(mutableListOf())
		}
		cursorRow = 0
		cursorCol = 0
	}

	/**
	 * 处理 ANSI 清除行命令
	 */
	fun clearLine() {
		ensureRow(cursorRow)
		buffer[cursorRow].clear()
		cursorCol = 0
	}

	/**
	 * 处理光标定位
	 */
	fun setCursor(row: Int, col: Int) {
		cursorRow = row.coerceIn(0, rows - 1)
		cursorCol = col.coerceIn(0, cols - 1)
		ensureRow(cursorRow)
	}

	/**
	 * 获取可见内容（最后 N 行）
	 */
	fun getVisibleContent(visibleRows: Int): List<List<CharCell>> {
		val startRow = (cursorRow - visibleRows + 1).coerceAtLeast(0)
		return buffer.subList(startRow, cursorRow + 1)
	}

	/**
	 * 获取所有内容
	 */
	fun getAllContent(): List<List<CharCell>> {
		return buffer.filter { it.isNotEmpty() }
	}

	/**
	 * 获取纯文本内容（用于复制等）
	 */
	fun getPlainText(): String {
		return buffer.joinToString("\n") { row ->
			row.joinToString("") { cell -> cell.char.toString() }
		}
	}
}

/**
 * 字符单元格
 */
data class CharCell(
	val char: Char,
	val fgColor: Color?,
	val bgColor: Color?,
	val isBold: Boolean
)

