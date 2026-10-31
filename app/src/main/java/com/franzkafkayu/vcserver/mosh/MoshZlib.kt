package com.franzkafkayu.vcserver.mosh

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

internal object MoshZlib {
	fun compress(src: ByteArray): ByteArray {
		val deflater = Deflater(Deflater.BEST_COMPRESSION)
		deflater.setInput(src)
		deflater.finish()
		val out = ByteArrayOutputStream()
		val buf = ByteArray(1024)
		while (!deflater.finished()) {
			val n = deflater.deflate(buf)
			if (n > 0) out.write(buf, 0, n) else break
		}
		deflater.end()
		return out.toByteArray()
	}

	fun decompress(src: ByteArray): ByteArray {
		val inflater = Inflater()
		inflater.setInput(src)
		val out = ByteArrayOutputStream()
		val buf = ByteArray(1024)
		try {
			while (!inflater.finished() && !inflater.needsDictionary()) {
				val n = inflater.inflate(buf)
				if (n == 0) {
					if (inflater.needsInput()) break
					if (n == 0 && inflater.finished()) break
					if (n == 0) break
				} else {
					out.write(buf, 0, n)
				}
			}
		} catch (_: Exception) {
			return src
		} finally {
			inflater.end()
		}
		val result = out.toByteArray()
		return if (result.isEmpty()) src else result
	}
}
