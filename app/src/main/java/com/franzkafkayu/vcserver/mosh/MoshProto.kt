package com.franzkafkayu.vcserver.mosh

internal object MoshProto {
	fun userKeystroke(keys: ByteArray): ByteArray {
		val keystroke = lengthDelimited(4, keys)
		val instruction = lengthDelimited(2, keystroke)
		return lengthDelimited(1, instruction)
	}

	fun userResize(cols: Int, rows: Int): ByteArray {
		val resize = varintField(5, cols.toLong()) + varintField(6, rows.toLong())
		val instruction = lengthDelimited(3, resize)
		return lengthDelimited(1, instruction)
	}

	fun transportInstruction(
		protocolVersion: Int,
		oldNum: Long,
		newNum: Long,
		ackNum: Long,
		throwawayNum: Long,
		diff: ByteArray,
		chaff: ByteArray = ByteArray(0)
	): ByteArray {
		var out = varintField(1, protocolVersion.toLong())
		out += varintField(2, oldNum)
		out += varintField(3, newNum)
		out += varintField(4, ackNum)
		out += varintField(5, throwawayNum)
		if (diff.isNotEmpty()) {
			out += lengthDelimited(6, diff)
		}
		if (chaff.isNotEmpty()) {
			out += lengthDelimited(7, chaff)
		}
		return out
	}

	fun parseTransportInstruction(bytes: ByteArray): TransportInstruction {
		var protocol = 0
		var oldNum = 0L
		var newNum = 0L
		var ackNum = 0L
		var throwawayNum = 0L
		var diff = ByteArray(0)
		var chaff = ByteArray(0)
		readFields(bytes) { field, value, packed ->
			when (field) {
				1 -> protocol = value.toInt()
				2 -> oldNum = value
				3 -> newNum = value
				4 -> ackNum = value
				5 -> throwawayNum = value
				6 -> if (packed != null) diff = packed
				7 -> if (packed != null) chaff = packed
			}
		}
		return TransportInstruction(protocol, oldNum, newNum, ackNum, throwawayNum, diff, chaff)
	}

	fun hostBytesFromDiff(diff: ByteArray): ByteArray {
		val out = ArrayList<Byte>()
		readFields(diff) { field, _, packed ->
			if (field == 1 && packed != null) {
				readFields(packed) { instField, _, instPacked ->
					if (instField == 2 && instPacked != null) {
						readFields(instPacked) { hbField, _, hbPacked ->
							if (hbField == 4 && hbPacked != null) {
								out.addAll(hbPacked.toList())
							}
						}
					}
				}
			}
		}
		return out.toByteArray()
	}

	private fun lengthDelimited(field: Int, value: ByteArray): ByteArray {
		val header = key(field, 2) + varint(value.size.toLong())
		return header + value
	}

	private fun varintField(field: Int, value: Long): ByteArray {
		return key(field, 0) + varint(value)
	}

	private fun key(field: Int, wire: Int): ByteArray = varint(((field shl 3) or wire).toLong())

	private fun varint(value: Long): ByteArray {
		var v = value
		val out = ArrayList<Byte>(10)
		while (v and 0x7FL.inv() != 0L) {
			out.add(((v and 0x7F) or 0x80).toByte())
			v = v ushr 7
		}
		out.add((v and 0x7F).toByte())
		return out.toByteArray()
	}

	private fun readFields(
		bytes: ByteArray,
		onField: (field: Int, varint: Long, packed: ByteArray?) -> Unit
	) {
		var i = 0
		while (i < bytes.size) {
			val (key, keySize) = readVarint(bytes, i)
			i += keySize
			val field = (key ushr 3).toInt()
			when ((key and 7).toInt()) {
				0 -> {
					val (value, size) = readVarint(bytes, i)
					i += size
					onField(field, value, null)
				}
				2 -> {
					val (len, size) = readVarint(bytes, i)
					i += size
					val end = i + len.toInt()
					if (end > bytes.size) return
					onField(field, 0, bytes.copyOfRange(i, end))
					i = end
				}
				else -> return
			}
		}
	}

	private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
		var result = 0L
		var shift = 0
		var i = start
		while (i < bytes.size) {
			val b = bytes[i].toInt() and 0xFF
			i++
			result = result or ((b and 0x7F).toLong() shl shift)
			if (b and 0x80 == 0) {
				return result to (i - start)
			}
			shift += 7
			if (shift > 63) break
		}
		return 0L to (i - start)
	}
}

internal data class TransportInstruction(
	val protocolVersion: Int,
	val oldNum: Long,
	val newNum: Long,
	val ackNum: Long,
	val throwawayNum: Long,
	val diff: ByteArray,
	val chaff: ByteArray
)
