package com.franzkafkayu.vcserver.utils

import com.franzkafkayu.vcserver.models.Server
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import kotlinx.coroutines.TimeoutCancellationException

/**
 * 服务器筛选器
 * 用于根据筛选条件匹配服务器
 */
class ServerFilter {
	
	companion object {
		private const val REGEX_TIMEOUT_MS = 100L
	}
	
	/**
	 * 检查服务器是否匹配条件
	 */
	fun matches(server: Server, condition: FilterCondition): Boolean {
		return when (condition) {
			is FilterCondition.FieldMatch -> matchesField(server, condition)
			is FilterCondition.And -> matches(server, condition.left) && matches(server, condition.right)
			is FilterCondition.Or -> matches(server, condition.left) || matches(server, condition.right)
		}
	}
	
	/**
	 * 匹配字段条件
	 */
	private fun matchesField(server: Server, fieldMatch: FilterCondition.FieldMatch): Boolean {
		val fieldValue = when (fieldMatch.field) {
			"name" -> server.name
			"host" -> server.host
			"port" -> server.port.toString()
			"username" -> server.username
			else -> return false
		}
		
		return if (fieldMatch.isRegex) {
			matchesRegex(fieldValue, fieldMatch.value, fieldMatch.ignoreCase)
		} else {
			// 模糊匹配（不区分大小写）
			fieldValue.contains(fieldMatch.value, ignoreCase = true)
		}
	}
	
	/**
	 * 正则表达式匹配（带超时保护）
	 */
	private fun matchesRegex(text: String, pattern: String, ignoreCase: Boolean): Boolean {
		return try {
			val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
			val regex = Pattern.compile(pattern, flags)
			regex.matcher(text).find()
		} catch (e: Exception) {
			// 正则表达式语法错误，返回 false
			false
		}
	}
	
	/**
	 * 简单文本匹配（在所有字段中搜索）
	 */
	fun matchesSimpleText(server: Server, query: String): Boolean {
		val lowerQuery = query.lowercase()
		return server.name.contains(lowerQuery, ignoreCase = true) ||
			server.host.contains(lowerQuery, ignoreCase = true) ||
			server.port.toString().contains(lowerQuery) ||
			server.username.contains(lowerQuery, ignoreCase = true)
	}
}
