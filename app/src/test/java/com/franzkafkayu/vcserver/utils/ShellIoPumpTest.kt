package com.franzkafkayu.vcserver.utils

import com.franzkafkayu.vcserver.mosh.TerminalIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class ShellIoPumpTest {
	@Test
	fun commandWriteStaysOpenAndFillsBuffer() {
		val io = LoopbackIo()
		val payload = ShellInput.commandLine("whoami")!!
		io.write(payload)
		assertTrue(io.isOpen())
		assertEquals(0x04.toByte(), ShellInput.EOF)
		assertTrue(payload.last() == ShellInput.ENTER)

		val raw = ByteArray(payload.size)
		val n = io.inputStream.read(raw)
		assertEquals(payload.size, n)
		val buffer = TerminalBuffer(screenRows = 8, cols = 40)
		buffer.writeBytes(raw.copyOf(n))
		assertTrue(buffer.getPlainText().contains("whoami"))
		assertTrue(io.isOpen())
	}

	private class LoopbackIo : TerminalIo {
		private val out = PipedOutputStream()
		override val inputStream = PipedInputStream(out, 4096)
		private var open = true

		override fun isOpen(): Boolean = open

		override fun write(bytes: ByteArray) {
			if (!open) throw java.io.IOException("SHELL_NOT_CONNECTED")
			out.write(bytes)
			out.flush()
		}

		override fun resize(cols: Int, rows: Int) {}

		override fun close() {
			open = false
			out.close()
			inputStream.close()
		}
	}
}
