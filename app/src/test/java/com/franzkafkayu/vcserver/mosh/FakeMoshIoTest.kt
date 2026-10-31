package com.franzkafkayu.vcserver.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class FakeMoshIoTest {
	@Test
	fun connectWriteReadResizeClose() {
		val fake = LoopbackIo()
		fake.write("hi".toByteArray())
		val buf = ByteArray(2)
		assertEqualsRead(fake, buf)
		assertArrayEquals("hi".toByteArray(), buf)
		fake.resize(80, 24)
		assertTrue(fake.resized)
		fake.close()
		assertTrue(!fake.isOpen())
	}

	private fun assertEqualsRead(io: TerminalIo, buf: ByteArray) {
		val n = io.inputStream.read(buf)
		assertTrue(n == buf.size)
	}

	private class LoopbackIo : TerminalIo {
		private val out = PipedOutputStream()
		override val inputStream = PipedInputStream(out, 1024)
		var resized = false
		private var open = true

		override fun isOpen(): Boolean = open

		override fun write(bytes: ByteArray) {
			out.write(bytes)
			out.flush()
		}

		override fun resize(cols: Int, rows: Int) {
			resized = true
		}

		override fun close() {
			open = false
			out.close()
			inputStream.close()
		}
	}
}
