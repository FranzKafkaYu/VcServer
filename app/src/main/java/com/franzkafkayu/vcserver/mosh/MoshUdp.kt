package com.franzkafkayu.vcserver.mosh

interface UdpLink {
	fun send(packet: ByteArray)
	fun receive(timeoutMs: Int): ByteArray?
	fun close()
}

class MoshUdp private constructor() : UdpLink {
	private var handle: Long = 0

	override fun send(packet: ByteArray) {
		if (handle == 0L) return
		nativeSend(handle, packet)
	}

	override fun receive(timeoutMs: Int): ByteArray? {
		if (handle == 0L) return null
		return nativeRecv(handle, timeoutMs)
	}

	override fun close() {
		val h = handle
		handle = 0L
		if (h != 0L) {
			nativeClose(h)
		}
	}

	private external fun nativeOpen(host: String, port: Int): Long
	private external fun nativeSend(handle: Long, packet: ByteArray): Int
	private external fun nativeRecv(handle: Long, timeoutMs: Int): ByteArray?
	private external fun nativeClose(handle: Long)

	companion object {
		@Volatile
		var loaded: Boolean = try {
			System.loadLibrary("vcmosh")
			true
		} catch (_: Throwable) {
			false
		}

		fun open(host: String, port: Int): MoshUdp {
			if (!loaded) {
				throw MoshException("MOSH_UDP_FAILED")
			}
			val udp = MoshUdp()
			val handle = udp.nativeOpen(host, port)
			if (handle == 0L) {
				throw MoshException("MOSH_UDP_FAILED")
			}
			udp.handle = handle
			return udp
		}
	}
}
