package com.franzkafkayu.vcserver.network

fun internetChecksum(data: ByteArray): Int {
	var sum = 0
	var i = 0
	while (i < data.size - 1) {
		sum += ((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)
		i += 2
	}
	if (data.size % 2 == 1) {
		sum += (data[data.size - 1].toInt() and 0xff) shl 8
	}
	while (sum ushr 16 != 0) {
		sum = (sum and 0xffff) + (sum ushr 16)
	}
	return sum.inv() and 0xffff
}

fun buildIcmpEchoV4(identifier: Int, sequence: Int): ByteArray {
	val packet = ByteArray(16)
	packet[0] = 8
	packet[1] = 0
	packet[4] = ((identifier ushr 8) and 0xff).toByte()
	packet[5] = (identifier and 0xff).toByte()
	packet[6] = ((sequence ushr 8) and 0xff).toByte()
	packet[7] = (sequence and 0xff).toByte()
	val sum = internetChecksum(packet)
	packet[2] = ((sum ushr 8) and 0xff).toByte()
	packet[3] = (sum and 0xff).toByte()
	return packet
}

/** Linux/Android SOCK_DGRAM ICMP：id 与 checksum 由内核填写。 */
fun buildKernelIcmpEchoV4(sequence: Int): ByteArray {
	val packet = ByteArray(8)
	packet[0] = 8
	packet[6] = ((sequence ushr 8) and 0xff).toByte()
	packet[7] = (sequence and 0xff).toByte()
	return packet
}

fun icmpPayloadOffset(packet: ByteArray): Int {
	if (packet.isEmpty()) return 0
	val version = (packet[0].toInt() ushr 4) and 0xf
	if (version == 4) {
		val ihl = packet[0].toInt() and 0xf
		return (ihl * 4).coerceAtMost(packet.size)
	}
	return 0
}

enum class IcmpKind { EchoReply, TimeExceeded, Other }

fun classifyIcmpV4(packet: ByteArray): IcmpKind {
	val offset = icmpPayloadOffset(packet)
	if (offset >= packet.size) return IcmpKind.Other
	return when (packet[offset].toInt() and 0xff) {
		0 -> IcmpKind.EchoReply
		11 -> IcmpKind.TimeExceeded
		else -> IcmpKind.Other
	}
}
