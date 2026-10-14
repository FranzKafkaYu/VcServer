package com.franzkafkayu.vcserver.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 命令历史的 JSON 编解码。持久化层只负责存这串文本。
 */
object CommandHistoryCodec {
	const val MAX_COMMAND_CHARS = 4096
	const val MAX_HISTORY = 100

	private val gson = Gson()

	fun encode(commands: List<String>): String {
		return gson.toJson(normalize(commands))
	}

	fun decode(raw: String?): List<String> {
		if (raw.isNullOrBlank()) return emptyList()
		return try {
			val parsed = gson.fromJson(raw, Array<String>::class.java) ?: return emptyList()
			normalize(parsed.toList())
		} catch (_: JsonSyntaxException) {
			emptyList()
		}
	}

	private fun normalize(commands: List<String>): List<String> {
		return commands
			.map { it.take(MAX_COMMAND_CHARS) }
			.takeLast(MAX_HISTORY)
	}
}
