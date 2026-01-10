package com.franzkafkayu.vcserver.utils

/**
 * 筛选条件树节点
 */
sealed class FilterCondition {
	/**
	 * 字段匹配条件
	 * @param field 字段名（name, host, port, username）
	 * @param value 匹配值
	 * @param isRegex 是否为正则表达式
	 * @param ignoreCase 是否忽略大小写（仅用于正则表达式）
	 */
	data class FieldMatch(
		val field: String,
		val value: String,
		val isRegex: Boolean = false,
		val ignoreCase: Boolean = false
	) : FilterCondition()

	/**
	 * AND 逻辑组合
	 */
	data class And(
		val left: FilterCondition,
		val right: FilterCondition
	) : FilterCondition()

	/**
	 * OR 逻辑组合
	 */
	data class Or(
		val left: FilterCondition,
		val right: FilterCondition
	) : FilterCondition()
}
