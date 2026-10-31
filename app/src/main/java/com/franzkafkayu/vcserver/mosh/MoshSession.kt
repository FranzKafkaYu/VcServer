package com.franzkafkayu.vcserver.mosh

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class MoshSession internal constructor(
	private val udp: UdpLink,
	private val crypto: MoshCrypto,
	cols: Int,
	rows: Int,
	private val stopRemote: (() -> Unit)? = null
) : TerminalIo {
	private val open = AtomicBoolean(true)
	private val pipeOut = PipedOutputStream()
	override val inputStream = PipedInputStream(pipeOut, 64 * 1024)
	private val sendSeq = AtomicLong(0)
	private val userStates = ArrayList<ByteArray>()
	private var lastAckedByRemote = 0L
	private var remoteNew = 0L
	private var instructionId = 1L
	private val sendLock = Any()
	private var receiveThread: Thread? = null

	init {
		live.incrementAndGet()
		userStates += MoshProto.userResize(cols, rows)
		receiveThread = thread(name = "mosh-recv", isDaemon = true) {
			receiveLoop()
		}
		sendCurrent()
	}

	fun awaitHost(timeoutMs: Long) {
		val deadline = System.currentTimeMillis() + timeoutMs
		while (System.currentTimeMillis() < deadline && open.get()) {
			if (gotHost.get()) return
			Thread.sleep(40)
		}
		if (!gotHost.get()) {
			close()
			throw MoshException("MOSH_UDP_FAILED")
		}
	}

	override fun isOpen(): Boolean = open.get()

	override fun write(bytes: ByteArray) {
		if (!open.get() || bytes.isEmpty()) return
		synchronized(sendLock) {
			userStates += MoshProto.userKeystroke(bytes)
			sendCurrent()
		}
	}

	override fun resize(cols: Int, rows: Int) {
		if (!open.get()) return
		synchronized(sendLock) {
			userStates += MoshProto.userResize(cols, rows)
			sendCurrent()
		}
	}

	override fun close() {
		if (!open.compareAndSet(true, false)) return
		try {
			sendShutdown()
		} catch (_: Exception) {
		}
		try {
			stopRemote?.invoke()
		} catch (_: Exception) {
		}
		try {
			udp.close()
		} catch (_: Exception) {
		}
		try {
			pipeOut.close()
		} catch (_: Exception) {
		}
		try {
			inputStream.close()
		} catch (_: Exception) {
		}
		live.decrementAndGet()
	}

	private val gotHost = AtomicBoolean(false)

	private fun sendShutdown() {
		val inst = MoshProto.transportInstruction(
			protocolVersion = 2,
			oldNum = lastAckedByRemote,
			newNum = -1L,
			ackNum = remoteNew,
			throwawayNum = lastAckedByRemote,
			diff = ByteArray(0)
		)
		MoshZlib.compress(inst).let { compressed ->
			fragment(compressed).forEach { sendFragment(it) }
		}
	}

	private fun sendCurrent() {
		if (!open.get()) return
		val newNum = userStates.size.toLong()
		val oldNum = lastAckedByRemote
		val diff = mergeFrom(oldNum)
		val inst = MoshProto.transportInstruction(
			protocolVersion = 2,
			oldNum = oldNum,
			newNum = newNum,
			ackNum = remoteNew,
			throwawayNum = 0,
			diff = diff
		)
		val compressed = MoshZlib.compress(inst)
		val fragments = fragment(compressed)
		fragments.forEach { sendFragment(it) }
	}

	private fun mergeFrom(oldNum: Long): ByteArray {
		val start = oldNum.toInt().coerceIn(0, userStates.size)
		var out = ByteArray(0)
		for (i in start until userStates.size) {
			out += userStates[i]
		}
		return out
	}

	private fun fragment(payload: ByteArray): List<ByteArray> {
		val mtu = 1024
		val parts = ArrayList<ByteArray>()
		var offset = 0
		var num = 0
		val id = instructionId++
		if (payload.isEmpty()) {
			parts += fragmentBytes(id, 0, true, ByteArray(0))
			return parts
		}
		while (offset < payload.size) {
			val end = (offset + mtu).coerceAtMost(payload.size)
			val last = end == payload.size
			parts += fragmentBytes(id, num, last, payload.copyOfRange(offset, end))
			offset = end
			num++
		}
		return parts
	}

	private fun fragmentBytes(id: Long, num: Int, finalFrag: Boolean, contents: ByteArray): ByteArray {
		val header = ByteArray(10)
		writeBe64(header, 0, id)
		val combined = ((if (finalFrag) 0x8000 else 0) or (num and 0x7FFF))
		header[8] = ((combined ushr 8) and 0xFF).toByte()
		header[9] = (combined and 0xFF).toByte()
		return header + contents
	}

	private fun sendFragment(fragment: ByteArray) {
		val ts = timestamp16()
		val plain = ByteArray(4 + fragment.size)
		plain[0] = ((ts ushr 8) and 0xFF).toByte()
		plain[1] = (ts and 0xFF).toByte()
		plain[2] = ((savedReply ushr 8) and 0xFF).toByte()
		plain[3] = (savedReply and 0xFF).toByte()
		System.arraycopy(fragment, 0, plain, 4, fragment.size)
		val seq = sendSeq.getAndIncrement()
		val packet = crypto.encrypt(MoshCrypto.clientSeq(seq), plain)
		udp.send(packet)
	}

	@Volatile
	private var savedReply = 0

	private val assembly = HashMap<Long, Array<ByteArray?>>()
	private val assemblyCount = HashMap<Long, Pair<Int, Int>>()

	private fun receiveLoop() {
		while (open.get()) {
			val packet = try {
				udp.receive(250)
			} catch (_: Exception) {
				null
			} ?: continue
			val decrypted = crypto.decrypt(packet) ?: continue
			val (seq, plain) = decrypted
			if (!MoshCrypto.isToClient(seq) || plain.size < 14) continue
			savedReply = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
			val frag = plain.copyOfRange(4, plain.size)
			onFragment(frag)
		}
	}

	private fun onFragment(raw: ByteArray) {
		if (raw.size < 10) return
		val id = readBe64(raw, 0)
		val combined = ((raw[8].toInt() and 0xFF) shl 8) or (raw[9].toInt() and 0xFF)
		val finalFrag = combined and 0x8000 != 0
		val num = combined and 0x7FFF
		val contents = raw.copyOfRange(10, raw.size)
		val slots = assembly.getOrPut(id) { arrayOfNulls(num + 1) }
		val grown = if (slots.size <= num) slots.copyOf(num + 1) else slots
		if (grown !== slots) assembly[id] = grown
		grown[num] = contents
		val (arrived, total) = assemblyCount[id] ?: (0 to -1)
		val newArrived = arrived + 1
		val newTotal = if (finalFrag) num + 1 else total
		assemblyCount[id] = newArrived to newTotal
		if (newTotal > 0) {
			val parts = assembly[id] ?: return
			if (parts.size < newTotal) return
			if ((0 until newTotal).any { parts[it] == null }) return
			assembly.remove(id)
			assemblyCount.remove(id)
			var joined = ByteArray(0)
			for (i in 0 until newTotal) {
				joined += parts[i]!!
			}
			onInstruction(MoshZlib.decompress(joined))
		}
	}

	private fun onInstruction(bytes: ByteArray) {
		val inst = try {
			MoshProto.parseTransportInstruction(bytes)
		} catch (_: Exception) {
			return
		}
		if (inst.ackNum > lastAckedByRemote) {
			lastAckedByRemote = inst.ackNum
		}
		if (inst.newNum <= remoteNew) return
		remoteNew = inst.newNum
		val host = MoshProto.hostBytesFromDiff(inst.diff)
		if (host.isNotEmpty()) {
			gotHost.set(true)
			try {
				pipeOut.write(host)
				pipeOut.flush()
			} catch (_: Exception) {
			}
		} else if (inst.diff.isNotEmpty()) {
			gotHost.set(true)
		}
		synchronized(sendLock) {
			sendCurrent()
		}
	}

	private fun timestamp16(): Int {
		return ((System.nanoTime() / 1_000_000L) shr 4).toInt() and 0xFFFF
	}

	private fun writeBe64(buf: ByteArray, offset: Int, value: Long) {
		var v = value
		for (i in offset + 7 downTo offset) {
			buf[i] = (v and 0xFF).toByte()
			v = v ushr 8
		}
	}

	private fun readBe64(buf: ByteArray, offset: Int): Long {
		var v = 0L
		for (i in 0 until 8) {
			v = (v shl 8) or (buf[offset + i].toLong() and 0xFF)
		}
		return v
	}

	companion object {
		private val live = AtomicInteger(0)
		fun liveCount(): Int = live.get()
	}
}

object MoshEngine {
	fun connect(
		host: String,
		port: Int,
		key: String,
		cols: Int,
		rows: Int,
		udpFactory: (String, Int) -> UdpLink = { h, p -> MoshUdp.open(h, p) },
		stopRemote: (() -> Unit)? = null
	): MoshSession {
		val udp = udpFactory(host, port)
		val session = try {
			MoshSession(udp, MoshCrypto(key), cols, rows, stopRemote)
		} catch (e: Exception) {
			udp.close()
			throw e
		}
		try {
			session.awaitHost(8_000)
		} catch (e: Exception) {
			session.close()
			throw e
		}
		return session
	}
}
