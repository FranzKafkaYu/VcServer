package com.franzkafkayu.vcserver.utils

import com.franzkafkayu.vcserver.models.CustomShellCommand
import com.franzkafkayu.vcserver.models.CustomShellCommandScope
import com.franzkafkayu.vcserver.models.Server
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.util.UUID

object CustomShellCommands {
	private val gson = Gson()

	fun sanitize(list: List<CustomShellCommand>): List<CustomShellCommand> {
		return list.mapNotNull { item -> normalize(item) }
	}

	fun save(
		existing: List<CustomShellCommand>,
		id: String?,
		title: String,
		body: String,
		scopeType: String = CustomShellCommandScope.GLOBAL,
		scopeId: Long? = null,
		groupIds: Set<Long> = emptySet(),
		serverIds: Set<Long> = emptySet()
	): List<CustomShellCommand>? {
		val item = normalize(
			CustomShellCommand(
				id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
				title = title,
				body = body,
				scopeType = scopeType,
				scopeId = scopeId
			)
		) ?: return null
		if (!isSaveableScope(item, groupIds, serverIds)) {
			return null
		}
		val index = existing.indexOfFirst { it.id == item.id }
		return if (index < 0) {
			existing + item
		} else {
			existing.toMutableList().also { it[index] = item }
		}
	}

	fun remove(existing: List<CustomShellCommand>, id: String): List<CustomShellCommand> {
		return existing.filter { it.id != id }
	}

	fun encode(list: List<CustomShellCommand>): String {
		return gson.toJson(sanitize(list))
	}

	fun decode(raw: String?): List<CustomShellCommand> {
		if (raw.isNullOrBlank()) return emptyList()
		return try {
			val element = JsonParser.parseString(raw)
			if (!element.isJsonArray) return emptyList()
			val parsed = element.asJsonArray.mapNotNull { entry ->
				if (!entry.isJsonObject) {
					return@mapNotNull null
				}
				val obj = entry.asJsonObject
				CustomShellCommand(
					id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
					title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
					body = obj.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
					scopeType = CustomShellCommandScope.normalize(
						obj.get("scopeType")?.takeIf { !it.isJsonNull }?.asString
					),
					scopeId = obj.get("scopeId")?.takeIf { !it.isJsonNull }?.asLong
				)
			}
			sanitize(parsed)
		} catch (_: JsonSyntaxException) {
			emptyList()
		} catch (_: IllegalStateException) {
			emptyList()
		} catch (_: NumberFormatException) {
			emptyList()
		}
	}

	fun forServer(list: List<CustomShellCommand>, server: Server): List<CustomShellCommand> {
		return sanitize(list).filter { appliesTo(it, server) }
	}

	fun appliesTo(command: CustomShellCommand, server: Server): Boolean {
		val item = normalize(command) ?: return false
		return when (item.scopeType) {
			CustomShellCommandScope.GLOBAL -> true
			CustomShellCommandScope.GROUP -> server.groupId != null && server.groupId == item.scopeId
			CustomShellCommandScope.SERVER -> server.id == item.scopeId
			else -> false
		}
	}

	fun isTargetMissing(
		command: CustomShellCommand,
		groupIds: Set<Long>,
		serverIds: Set<Long>
	): Boolean {
		val item = normalize(command) ?: return false
		return !isSaveableScope(item, groupIds, serverIds)
	}

	private fun normalize(item: CustomShellCommand): CustomShellCommand? {
		val title = item.title.trim()
		val body = item.body.trim()
		if (item.id.isBlank() || title.isEmpty() || body.isEmpty()) {
			return null
		}
		val type = CustomShellCommandScope.normalize(item.scopeType)
		val scopeId = when (type) {
			CustomShellCommandScope.GLOBAL -> null
			CustomShellCommandScope.GROUP, CustomShellCommandScope.SERVER ->
				item.scopeId?.takeIf { it > 0 } ?: return null
			else -> null
		}
		return item.copy(title = title, body = body, scopeType = type, scopeId = scopeId)
	}

	private fun isSaveableScope(
		item: CustomShellCommand,
		groupIds: Set<Long>,
		serverIds: Set<Long>
	): Boolean {
		return when (item.scopeType) {
			CustomShellCommandScope.GLOBAL -> true
			CustomShellCommandScope.GROUP -> item.scopeId != null && groupIds.contains(item.scopeId)
			CustomShellCommandScope.SERVER -> item.scopeId != null && serverIds.contains(item.scopeId)
			else -> false
		}
	}
}
