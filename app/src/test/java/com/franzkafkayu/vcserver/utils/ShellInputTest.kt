package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ShellInputTest {
	@Test
	fun whoamiIsCrTerminatedNotEof() {
		val bytes = ShellInput.commandLine("  whoami  ")!!
		assertEquals("whoami\r", String(bytes, Charsets.UTF_8))
		assertFalse(bytes.contentEquals(byteArrayOf(ShellInput.EOF)))
		assertEquals(ShellInput.ENTER, bytes.last())
	}

	@Test
	fun blankCommandIsNotSent() {
		assertNull(ShellInput.commandLine("   "))
	}

	@Test
	fun commandBytesAreNotEofControl() {
		val bytes = ShellInput.commandLine("whoami")!!
		assertFalse(bytes.contains(ShellInput.EOF))
	}
}

class ShellReadTest {
	@Test
	fun zeroLengthDoesNotClose() {
		assertEquals(ShellReadAction.SkipEmpty, ShellRead.classify(0))
	}

	@Test
	fun eofCloses() {
		assertEquals(ShellReadAction.Closed, ShellRead.classify(-1))
	}

	@Test
	fun positiveEmits() {
		assertEquals(ShellReadAction.Emit, ShellRead.classify(6))
	}

	@Test
	fun exceptionWhileOpenDoesNotClose() {
		assertEquals(ShellReadAction.SkipEmpty, ShellRead.classifyException(true))
		assertEquals(ShellReadAction.Closed, ShellRead.classifyException(false))
	}
}
