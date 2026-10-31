package com.franzkafkayu.vcserver.mosh

import org.bouncycastle.jcajce.spec.AEADParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal class MoshCrypto(printableKey: String) {
	private val key: SecretKeySpec

	init {
		if (printableKey.length != 22) {
			throw MoshException("MOSH_PARSE_FAILED")
		}
		val padded = printableKey + "=="
		val decoded = try {
			Base64.getDecoder().decode(padded)
		} catch (_: Exception) {
			throw MoshException("MOSH_PARSE_FAILED")
		}
		if (decoded.size != 16) {
			throw MoshException("MOSH_PARSE_FAILED")
		}
		key = SecretKeySpec(decoded, "AES")
		ensureProvider()
	}

	fun encrypt(seq: Long, plaintext: ByteArray): ByteArray {
		val nonce12 = nonce(seq)
		val cipher = Cipher.getInstance("AES/OCB/NoPadding", "BC")
		cipher.init(Cipher.ENCRYPT_MODE, key, AEADParameterSpec(nonce12, 128))
		val sealed = cipher.doFinal(plaintext)
		return nonce12.copyOfRange(4, 12) + sealed
	}

	fun decrypt(packet: ByteArray): Pair<Long, ByteArray>? {
		if (packet.size < 24) return null
		val nonce12 = ByteArray(12)
		System.arraycopy(packet, 0, nonce12, 4, 8)
		return try {
			val cipher = Cipher.getInstance("AES/OCB/NoPadding", "BC")
			cipher.init(Cipher.DECRYPT_MODE, key, AEADParameterSpec(nonce12, 128))
			val plain = cipher.doFinal(packet, 8, packet.size - 8)
			val seq = seqFromNonce(nonce12)
			seq to plain
		} catch (_: Exception) {
			null
		}
	}

	companion object {
		private const val TO_CLIENT = 1L shl 63

		fun clientSeq(seq: Long): Long = seq and TO_CLIENT.inv()

		fun isToClient(seq: Long): Boolean = seq and TO_CLIENT != 0L

		private fun nonce(seq: Long): ByteArray {
			val out = ByteArray(12)
			var v = seq
			for (i in 11 downTo 4) {
				out[i] = (v and 0xFF).toByte()
				v = v ushr 8
			}
			return out
		}

		private fun seqFromNonce(nonce12: ByteArray): Long {
			var v = 0L
			for (i in 4..11) {
				v = (v shl 8) or (nonce12[i].toLong() and 0xFF)
			}
			return v
		}

		private fun ensureProvider() {
			if (Security.getProvider("BC") == null) {
				Security.addProvider(BouncyCastleProvider())
			}
		}
	}
}
