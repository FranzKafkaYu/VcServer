package com.franzkafkayu.vcserver.mosh

import java.io.InputStream

interface TerminalIo {
	val inputStream: InputStream
	fun isOpen(): Boolean
	fun write(bytes: ByteArray)
	fun resize(cols: Int, rows: Int)
	fun close()
}
