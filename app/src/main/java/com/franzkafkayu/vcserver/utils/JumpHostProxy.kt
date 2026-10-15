package com.franzkafkayu.vcserver.utils

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * 在已登录的跳板 Session 上打开 direct-tcpip，供目标 SSH 握手使用。
 */
class JumpHostProxy(
	private val jumpSession: Session
) : Proxy {
	private var channel: ChannelDirectTCPIP? = null
	private var input: InputStream? = null
	private var output: OutputStream? = null

	override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
		val opened = jumpSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
		opened.setHost(host)
		opened.setPort(port)
		// 必须在 connect 之前拿到流。否则目标的 SSH 欢迎语会在管道建立前到达并被丢掉，
		// 随后握手会一直堵到对端空闲断开（常见约 120 秒）。
		input = opened.inputStream
		output = opened.outputStream
		opened.connect(timeout)
		channel = opened
	}

	override fun getInputStream(): InputStream {
		return input ?: throw IllegalStateException("jump proxy is not connected")
	}

	override fun getOutputStream(): OutputStream {
		return output ?: throw IllegalStateException("jump proxy is not connected")
	}

	override fun getSocket(): Socket? = null

	override fun close() {
		try {
			channel?.disconnect()
		} catch (_: Exception) {
		}
		channel = null
		input = null
		output = null
	}
}
