package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {
	private fun buffer() = TerminalBuffer(screenRows = 10, cols = 40)

	@Test
	fun overwriteSameCellInsteadOfAppending() {
		val terminal = buffer()
		terminal.write("AB")
		terminal.write("\u001B[1;1HX")
		assertEquals('X', terminal.charAt(0, 0))
		assertEquals('B', terminal.charAt(0, 1))
		terminal.write("\u001B[2;1HAB")
		terminal.write("\u001B[2;1HXY")
		assertEquals('X', terminal.charAt(1, 0))
		assertEquals('Y', terminal.charAt(1, 1))
	}

	@Test
	fun eraseLineClearsFromCursor() {
		val terminal = buffer()
		terminal.write("HELLO")
		terminal.write("\u001B[1;1H\u001B[K")
		assertEquals(' ', terminal.charAt(0, 0))
		assertEquals(' ', terminal.charAt(0, 4))
		assertFalse(terminal.getPlainText().contains("HELLO"))
	}

	@Test
	fun alternateScreenRestoresPrimary() {
		val terminal = buffer()
		terminal.write("shell-line")
		terminal.write("\u001B[?1049h")
		terminal.write("\u001B[1;1HHTOP")
		assertTrue(terminal.isAlternateScreen())
		assertEquals('H', terminal.charAt(0, 0))
		terminal.write("\u001B[?1049l")
		assertFalse(terminal.isAlternateScreen())
		assertTrue(terminal.getPlainText().contains("shell-line"))
		assertFalse(terminal.getPlainText().contains("HTOP"))
	}

	@Test
	fun splitCursorSequenceIsNotVisible() {
		val terminal = buffer()
		terminal.write("\u001B[")
		terminal.write("2;3Hxy")
		assertEquals('x', terminal.charAt(1, 2))
		assertEquals('y', terminal.charAt(1, 3))
		assertFalse(terminal.getPlainText().contains("["))
		assertFalse(terminal.getPlainText().contains("\u001B"))
	}

	@Test
	fun splitUtf8StaysPending() {
		val terminal = buffer()
		val bytes = "中".toByteArray(Charsets.UTF_8)
		terminal.writeBytes(byteArrayOf(bytes[0]))
		assertEquals(' ', terminal.charAt(0, 0))
		terminal.writeBytes(bytes.copyOfRange(1, bytes.size))
		assertEquals('中', terminal.charAt(0, 0))
	}
}
