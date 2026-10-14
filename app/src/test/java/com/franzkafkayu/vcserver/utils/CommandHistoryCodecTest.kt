package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryCodecTest {
	@Test
	fun roundTripPreservesOrder() {
		val commands = listOf("ls", "cd /tmp", "pwd")
		assertEquals(commands, CommandHistoryCodec.decode(CommandHistoryCodec.encode(commands)))
	}

	@Test
	fun truncatesCommandLengthAndHistorySize() {
		val longCommand = "a".repeat(CommandHistoryCodec.MAX_COMMAND_CHARS + 20)
		val many = (1..CommandHistoryCodec.MAX_HISTORY + 5).map { "cmd$it" } + longCommand
		val decoded = CommandHistoryCodec.decode(CommandHistoryCodec.encode(many))
		assertEquals(CommandHistoryCodec.MAX_HISTORY, decoded.size)
		assertEquals("cmd7", decoded.first())
		assertEquals(CommandHistoryCodec.MAX_COMMAND_CHARS, decoded.last().length)
	}

	@Test
	fun invalidJsonDecodesToEmpty() {
		assertTrue(CommandHistoryCodec.decode("{").isEmpty())
		assertTrue(CommandHistoryCodec.decode(null).isEmpty())
		assertTrue(CommandHistoryCodec.decode("").isEmpty())
	}
}
