package com.franzkafkayu.vcserver.utils

import java.util.regex.PatternSyntaxException

/**
 * Filter query parser for server filtering
 * Supports:
 * - Simple text search (searches all fields)
 * - Field-specific search: name:value, host:value
 * - AND logic: condition1 AND condition2
 * - OR logic: condition1 OR condition2
 * - Regular expressions: field:/pattern/ or field:/pattern/i
 * - Parentheses for grouping expressions
 */
class FilterQueryParser {
	
	companion object {
		private val FIELD_PATTERN = Regex("""^(name|host|port|username):(.+)$""")
		private val REGEX_PATTERN = Regex("""^/(.+?)/([i]*)$""")
		private val SUPPORTED_FIELDS = setOf("name", "host", "port", "username")
	}
	
	/**
	 * 解析查询字符串
	 */
	fun parse(query: String): Result<FilterCondition> {
		val trimmed = query.trim()
		if (trimmed.isEmpty()) {
			return Result.failure(IllegalArgumentException("查询字符串为空"))
		}
		
		return try {
			val condition = parseExpression(trimmed)
			Result.success(condition)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}
	
	/**
	 * 解析表达式（处理括号和运算符优先级）
	 */
	private fun parseExpression(input: String): FilterCondition {
		var expr = input.trim()
		
		// 处理括号
		while (expr.startsWith('(') && expr.endsWith(')')) {
			val inner = expr.substring(1, expr.length - 1)
			if (isBalanced(inner)) {
				expr = inner.trim()
			} else {
				break
			}
		}
		
		// 按优先级解析：AND 优先级高于 OR（标准逻辑运算符优先级）
		// 先分割AND（在括号外），再分割OR
		
		// 先分割AND（在括号外）
		val andParts = splitByOperator(expr, " AND ", preserveCase = true)
		if (andParts.size > 1) {
			return andParts.map { parseExpression(it) }
				.reduce { acc, condition -> FilterCondition.And(acc, condition) }
		}
		
		// 再分割OR（在括号外）
		val orParts = splitByOperator(expr, " OR ", preserveCase = true)
		if (orParts.size > 1) {
			return orParts.map { parseExpression(it) }
				.reduce { acc, condition -> FilterCondition.Or(acc, condition) }
		}
		
		// 解析单个条件
		return parseSingleCondition(expr)
	}
	
	/**
	 * 按运算符分割（考虑括号）
	 */
	private fun splitByOperator(input: String, operator: String, preserveCase: Boolean = false): List<String> {
		val parts = mutableListOf<String>()
		var current = StringBuilder()
		var depth = 0
		var i = 0
		
		while (i < input.length) {
			if (input[i] == '(') {
				depth++
				current.append(input[i])
			} else if (input[i] == ')') {
				depth--
				current.append(input[i])
			} else if (depth == 0 && i + operator.length <= input.length) {
				val substr = input.substring(i, i + operator.length)
				if (substr.equals(operator, !preserveCase)) {
					parts.add(current.toString().trim())
					current.clear()
					i += operator.length - 1  // -1 因为循环末尾会 i++
				} else {
					current.append(input[i])
				}
			} else {
				current.append(input[i])
			}
			i++
		}
		
		if (current.isNotEmpty()) {
			parts.add(current.toString().trim())
		}
		
		return if (parts.size > 1) parts else listOf(input)
	}
	
	/**
	 * 检查括号是否平衡
	 */
	private fun isBalanced(input: String): Boolean {
		var depth = 0
		for (char in input) {
			when (char) {
				'(' -> depth++
				')' -> {
					depth--
					if (depth < 0) return false
				}
			}
		}
		return depth == 0
	}
	
	/**
	 * 检查查询是否是简单文本（不包含字段指定、AND、OR等语法）
	 */
	fun isSimpleText(query: String): Boolean {
		val trimmed = query.trim()
		// 如果包含字段指定（:）、逻辑运算符（AND/OR）、正则表达式（/.../），则不是简单文本
		return !trimmed.contains(':') && 
			!trimmed.contains(" AND ", ignoreCase = true) && 
			!trimmed.contains(" OR ", ignoreCase = true) &&
			!trimmed.contains(Regex("""/\w+/"""))
	}
	
	/**
	 * 解析单个条件（字段匹配）
	 */
	private fun parseSingleCondition(input: String): FilterCondition {
		val trimmed = input.trim()
		
		// 尝试解析字段指定语法
		val fieldMatch = FIELD_PATTERN.find(trimmed)
		if (fieldMatch != null) {
			val field = fieldMatch.groupValues[1]
			val value = fieldMatch.groupValues[2]
			
			if (field !in SUPPORTED_FIELDS) {
				throw IllegalArgumentException("不支持的字段: $field")
			}
			
			// 检查是否是正则表达式
			val regexMatch = REGEX_PATTERN.find(value)
			if (regexMatch != null) {
				val regexPattern = regexMatch.groupValues[1]
				val flags = regexMatch.groupValues[2]
				val ignoreCase = flags.contains('i', ignoreCase = true)
				
				// 验证正则表达式语法
				try {
					java.util.regex.Pattern.compile(regexPattern)
				} catch (e: PatternSyntaxException) {
					throw IllegalArgumentException("正则表达式语法错误: ${e.message}")
				}
				
				return FilterCondition.FieldMatch(
					field = field,
					value = regexPattern,
					isRegex = true,
					ignoreCase = ignoreCase
				)
			} else {
				// 普通字段匹配
				return FilterCondition.FieldMatch(
					field = field,
					value = value
				)
			}
		}
		
		throw IllegalArgumentException("无法解析条件: $trimmed")
	}
}