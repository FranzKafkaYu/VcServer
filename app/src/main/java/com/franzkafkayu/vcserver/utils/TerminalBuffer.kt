package com.franzkafkayu.vcserver.utils

import androidx.compose.ui.graphics.Color

/**
 * 终端缓冲区。
 * 主屏幕保留滚动历史并按空白折行；备用屏幕是与 PTY 同尺寸的固定格子。
 */
class TerminalBuffer(
	screenRows: Int = 24,
	cols: Int = 80,
	private val scrollbackLimit: Int = 2000
) {
	private var screenRows = screenRows.coerceAtLeast(1)
	private var cols = cols.coerceAtLeast(1)
	private var primary = newGrid(this.screenRows, this.cols)
	private var alternate = newGrid(this.screenRows, this.cols)
	private val scrollback = ArrayDeque<Array<CharCell>>()
	private val stream = AnsiStream()
	private var pendingUtf8 = ByteArray(0)

	private var activeIsAlt = false
	private var cursorRow = 0
	private var cursorCol = 0
	private var savedCursorRow = 0
	private var savedCursorCol = 0
	private var markedRow = 0
	private var markedCol = 0
	private var scrollTop = 0
	private var scrollBottom = this.screenRows - 1
	private var fg: Color? = null
	private var bg: Color? = null
	private var bold = false
	private var applicationCursor = false
	var frame: Int = 0
		private set

	fun isAlternateScreen(): Boolean = activeIsAlt

	fun applicationCursorKeys(): Boolean = applicationCursor

	fun cols(): Int = cols

	fun rows(): Int = screenRows

	fun setSize(newCols: Int, newRows: Int) {
		val c = newCols.coerceAtLeast(1)
		val r = newRows.coerceAtLeast(1)
		if (c == cols && r == screenRows) return
		primary = refit(primary, r, c)
		alternate = refit(alternate, r, c)
		cols = c
		screenRows = r
		cursorRow = cursorRow.coerceIn(0, r - 1)
		cursorCol = cursorCol.coerceIn(0, c - 1)
		savedCursorRow = savedCursorRow.coerceIn(0, r - 1)
		savedCursorCol = savedCursorCol.coerceIn(0, c - 1)
		scrollTop = 0
		scrollBottom = r - 1
		touch()
	}

	fun setCols(newCols: Int) {
		setSize(newCols, screenRows)
	}

	fun writeBytes(data: ByteArray) {
		if (data.isEmpty() && pendingUtf8.isEmpty()) return
		val all = if (pendingUtf8.isEmpty()) data else pendingUtf8 + data
		val text = StringBuilder()
		var i = 0
		while (i < all.size) {
			val lead = all[i].toInt() and 0xFF
			val need = utf8Length(lead)
			if (need < 0) {
				text.append('\uFFFD')
				i++
				continue
			}
			if (i + need > all.size) {
				pendingUtf8 = all.copyOfRange(i, all.size)
				if (text.isNotEmpty()) write(text.toString())
				return
			}
			if (!isValidUtf8(all, i, need)) {
				text.append('\uFFFD')
				i++
				continue
			}
			text.append(String(all, i, need, Charsets.UTF_8))
			i += need
		}
		pendingUtf8 = ByteArray(0)
		if (text.isNotEmpty()) write(text.toString())
	}

	fun write(text: String) {
		if (text.isEmpty()) return
		for (action in stream.feed(text)) {
			when (action) {
				is AnsiAction.Print -> printText(action.text)
				is AnsiAction.Style -> {
					fg = action.fg
					bg = action.bg
					bold = action.bold
				}
				is AnsiAction.Cup -> {
					if (action.row > 0) cursorRow = (action.row - 1).coerceIn(0, screenRows - 1)
					if (action.col > 0) cursorCol = (action.col - 1).coerceIn(0, cols - 1)
				}
				is AnsiAction.Move -> move(action.command, action.count)
				is AnsiAction.EraseDisplay -> eraseDisplay(action.mode)
				is AnsiAction.EraseLine -> eraseLine(action.mode)
				is AnsiAction.EraseChars -> eraseChars(action.count)
				is AnsiAction.ScrollRegion -> setScrollRegion(action.top, action.bottom)
				is AnsiAction.InsertLines -> insertLines(action.count)
				is AnsiAction.DeleteLines -> deleteLines(action.count)
				is AnsiAction.CursorMark -> {
					if (action.restore) {
						cursorRow = markedRow.coerceIn(0, screenRows - 1)
						cursorCol = markedCol.coerceIn(0, cols - 1)
					} else {
						markedRow = cursorRow
						markedCol = cursorCol
					}
				}
				is AnsiAction.AltScreen -> if (action.enter) enterAlt() else exitAlt()
				is AnsiAction.DecCkm -> applicationCursor = action.application
				AnsiAction.Index -> index()
				AnsiAction.ReverseIndex -> reverseIndex()
			}
		}
		touch()
	}

	fun charAt(row: Int, col: Int): Char {
		val grid = screen()
		if (row !in grid.indices || col !in 0 until cols) return ' '
		return grid[row][col].char
	}

	fun clearScreen() {
		clearGrid(screen())
		if (!activeIsAlt) scrollback.clear()
		cursorRow = 0
		cursorCol = 0
		touch()
	}

	fun getAllContent(): List<List<CharCell>> {
		if (activeIsAlt) {
			return alternate.map { it.toList() }
		}
		val last = maxOf(cursorRow, lastContentRow(primary))
		val visible = (0..last).map { primary[it].toList() }
		return scrollback.map { it.toList() } + visible
	}

	fun getPlainText(): String {
		return getAllContent().joinToString("\n") { row ->
			row.joinToString("") { it.char.toString() }.trimEnd()
		}
	}

	private fun printText(text: String) {
		for (char in text) {
			when (char) {
				'\n' -> newLine()
				'\r' -> cursorCol = 0
				'\b' -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
				'\t' -> writeTab()
				else -> {
					if (char == '\u0007' || char.code < 32) continue
					writeChar(char)
				}
			}
		}
	}

	private fun writeChar(char: Char) {
		if (cursorCol >= cols) {
			if (activeIsAlt) {
				newLine()
			} else {
				wrapPrimary()
			}
		}
		if (cursorCol >= cols) {
			newLine()
		}
		screen()[cursorRow][cursorCol] = CharCell(char, fg, bg, bold)
		cursorCol++
	}

	private fun writeTab() {
		val width = if (activeIsAlt) 8 else 4
		val next = ((cursorCol / width) + 1) * width
		while (cursorCol < next && cursorCol < cols) {
			writeChar(' ')
		}
	}

	private fun wrapPrimary() {
		val row = primary[cursorRow]
		var split = -1
		for (i in cols - 1 downTo 0) {
			if (row[i].char == ' ') {
				split = i
				break
			}
		}
		if (split in 1 until cols - 1) {
			val tail = row.copyOfRange(split + 1, cols)
			var end = tail.size
			while (end > 0 && tail[end - 1].char == ' ') end--
			val rest = tail.copyOfRange(0, end)
			for (i in split until cols) {
				row[i] = blankCell()
			}
			newLine()
			for (cell in rest) {
				if (cursorCol >= cols) newLine()
				screen()[cursorRow][cursorCol] = cell
				cursorCol++
			}
		} else {
			newLine()
		}
	}

	private fun newLine() {
		index()
		cursorCol = 0
	}

	private fun index() {
		if (cursorRow >= scrollBottom) {
			scrollUp()
		} else {
			cursorRow++
		}
	}

	private fun reverseIndex() {
		if (cursorRow <= scrollTop) {
			scrollDown()
		} else {
			cursorRow--
		}
	}

	private fun scrollUp() {
		val grid = screen()
		if (!activeIsAlt && scrollTop == 0) {
			scrollback.addLast(grid[0].copyOf())
			while (scrollback.size > scrollbackLimit) {
				scrollback.removeFirst()
			}
		}
		for (r in scrollTop until scrollBottom) {
			grid[r] = grid[r + 1].copyOf()
		}
		grid[scrollBottom] = blankRow()
	}

	private fun scrollDown() {
		val grid = screen()
		for (r in scrollBottom downTo scrollTop + 1) {
			grid[r] = grid[r - 1].copyOf()
		}
		grid[scrollTop] = blankRow()
	}

	private fun move(command: Char, count: Int) {
		val n = count.coerceAtLeast(1)
		when (command) {
			'A' -> cursorRow = (cursorRow - n).coerceAtLeast(0)
			'B' -> cursorRow = (cursorRow + n).coerceAtMost(screenRows - 1)
			'C' -> cursorCol = (cursorCol + n).coerceAtMost(cols - 1)
			'D' -> cursorCol = (cursorCol - n).coerceAtLeast(0)
		}
	}

	private fun eraseDisplay(mode: Int) {
		val grid = screen()
		when (mode) {
			1 -> {
				for (r in 0 until cursorRow) clearRow(grid[r])
				for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = erasedCell()
			}
			2, 3 -> {
				clearGrid(grid)
				if (mode == 3 && !activeIsAlt) scrollback.clear()
			}
			else -> {
				for (c in cursorCol until cols) grid[cursorRow][c] = erasedCell()
				for (r in cursorRow + 1 until screenRows) clearRow(grid[r])
			}
		}
	}

	private fun eraseLine(mode: Int) {
		val row = screen()[cursorRow]
		when (mode) {
			1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) row[c] = erasedCell()
			2 -> clearRow(row)
			else -> for (c in cursorCol until cols) row[c] = erasedCell()
		}
	}

	private fun eraseChars(count: Int) {
		val row = screen()[cursorRow]
		val end = (cursorCol + count).coerceAtMost(cols)
		for (c in cursorCol until end) row[c] = erasedCell()
	}

	private fun setScrollRegion(top: Int, bottom: Int) {
		if (top <= 0 || bottom <= 0) {
			scrollTop = 0
			scrollBottom = screenRows - 1
			return
		}
		val t = (top - 1).coerceIn(0, screenRows - 1)
		val b = (bottom - 1).coerceIn(0, screenRows - 1)
		if (t >= b) {
			scrollTop = 0
			scrollBottom = screenRows - 1
		} else {
			scrollTop = t
			scrollBottom = b
		}
	}

	private fun insertLines(count: Int) {
		val grid = screen()
		val row = cursorRow.coerceIn(scrollTop, scrollBottom)
		repeat(count.coerceAtLeast(1)) {
			for (r in scrollBottom downTo row + 1) {
				grid[r] = grid[r - 1].copyOf()
			}
			grid[row] = blankRow()
		}
	}

	private fun deleteLines(count: Int) {
		val grid = screen()
		val row = cursorRow.coerceIn(scrollTop, scrollBottom)
		repeat(count.coerceAtLeast(1)) {
			for (r in row until scrollBottom) {
				grid[r] = grid[r + 1].copyOf()
			}
			grid[scrollBottom] = blankRow()
		}
	}

	private fun enterAlt() {
		if (activeIsAlt) return
		savedCursorRow = cursorRow
		savedCursorCol = cursorCol
		activeIsAlt = true
		clearGrid(alternate)
		cursorRow = 0
		cursorCol = 0
		scrollTop = 0
		scrollBottom = screenRows - 1
	}

	private fun exitAlt() {
		if (!activeIsAlt) return
		activeIsAlt = false
		cursorRow = savedCursorRow.coerceIn(0, screenRows - 1)
		cursorCol = savedCursorCol.coerceIn(0, cols - 1)
		scrollTop = 0
		scrollBottom = screenRows - 1
		clearGrid(alternate)
	}

	private fun screen(): Array<Array<CharCell>> = if (activeIsAlt) alternate else primary

	private fun blankCell() = CharCell(' ', null, null, false)

	private fun erasedCell() = CharCell(' ', null, bg, false)

	private fun blankRow(width: Int = cols) = Array(width) { blankCell() }

	private fun newGrid(rows: Int, width: Int) = Array(rows) { blankRow(width) }

	private fun clearRow(row: Array<CharCell>) {
		for (c in row.indices) row[c] = erasedCell()
	}

	private fun clearGrid(grid: Array<Array<CharCell>>) {
		for (row in grid) clearRow(row)
	}

	private fun refit(grid: Array<Array<CharCell>>, rows: Int, width: Int): Array<Array<CharCell>> {
		val next = Array(rows) { blankRow(width) }
		val copyRows = minOf(grid.size, rows)
		for (r in 0 until copyRows) {
			val copyCols = minOf(grid[r].size, width)
			for (c in 0 until copyCols) next[r][c] = grid[r][c]
		}
		return next
	}

	private fun lastContentRow(grid: Array<Array<CharCell>>): Int {
		for (r in grid.indices.reversed()) {
			if (grid[r].any { it.char != ' ' }) return r
		}
		return 0
	}

	private fun touch() {
		frame++
	}

	private fun utf8Length(lead: Int): Int = when {
		lead < 0x80 -> 1
		lead in 0xC2..0xDF -> 2
		lead in 0xE0..0xEF -> 3
		lead in 0xF0..0xF4 -> 4
		else -> -1
	}

	private fun isValidUtf8(data: ByteArray, start: Int, need: Int): Boolean {
		for (i in 1 until need) {
			val b = data[start + i].toInt() and 0xFF
			if (b !in 0x80..0xBF) return false
		}
		return true
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
