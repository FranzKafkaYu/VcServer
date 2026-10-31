package com.franzkafkayu.vcserver.mosh

import com.franzkafkayu.vcserver.ssh.SshShellChannel
import java.io.InputStream

class SshTerminalIo(private val channel: SshShellChannel) : TerminalIo {
	override val inputStream: InputStream
		get() = channel.inputStream

	override fun isOpen(): Boolean = channel.isOpen()

	override fun write(bytes: ByteArray) {
		if (!channel.isOpen()) return
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
