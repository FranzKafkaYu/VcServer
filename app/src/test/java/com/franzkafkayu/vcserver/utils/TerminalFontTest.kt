package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalFontTest {
	@Test
	fun coerceKnownStepsAndOutOfRange() {
		assertEquals(12, TerminalFont.coerce(12))
		assertEquals(10, TerminalFont.coerce(9))
		assertEquals(10, TerminalFont.coerce(11))
		assertEquals(12, TerminalFont.coerce(13))
		assertEquals(18, TerminalFont.coerce(100))
	}

	@Test
	fun largerAndSmallerStayInsideSteps() {
		assertEquals(14, TerminalFont.larger(12))
		assertEquals(18, TerminalFont.larger(18))
		assertEquals(10, TerminalFont.smaller(12))
		assertEquals(10, TerminalFont.smaller(10))
	}
}
