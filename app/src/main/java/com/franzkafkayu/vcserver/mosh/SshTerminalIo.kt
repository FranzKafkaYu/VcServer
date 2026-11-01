package com.franzkafkayu.vcserver.mosh

import com.franzkafkayu.vcserver.ssh.SshShellChannel
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

class SshTerminalIo(private val channel: SshShellChannel) : TerminalIo {
	override val inputStream: InputStream
		get() = channel.inputStream

	init {
		thread(name = "ssh-shell-stderr", isDaemon = true) {
			val buf = ByteArray(1024)
			try {
				val err = channel.errorStream
				while (channel.isOpen()) {
					val n = err.read(buf)
					if (n < 0) break
				}
			} catch (_: Exception) {
			}
		}
	}

	override fun isOpen(): Boolean = channel.isOpen()

	override fun write(bytes: ByteArray) {
		if (!channel.isOpen()) {
			throw IOException("SHELL_NOT_CONNECTED")
		}
		channel.outputStream.write(bytes)
		channel.outputStream.flush()
	}

	override fun resize(cols: Int, rows: Int) {
		channel.resize(cols, rows)
	}

	override fun close() {
		channel.close()
	}
}
