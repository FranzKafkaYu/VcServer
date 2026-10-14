package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandHistoryTest {
	@Test
	fun restoreThenPreviousWalksSavedCommands() {
		val history = CommandHistory()
		history.restore(listOf("ls", "pwd", "whoami"))
		assertEquals("whoami", history.getPreviousCommand())
		assertEquals("pwd", history.getPreviousCommand())
		assertEquals("ls", history.getPreviousCommand())
	}

	@Test
	fun snapshotMatchesRestoredOrder() {
		val history = CommandHistory()
		val saved = listOf("ls -l", "cd /var")
		history.restore(saved)
		assertEquals(saved, history.snapshot())
	}
}
