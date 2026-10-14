package com.franzkafkayu.vcserver.utils

/**
 * 终端字号分档。只使用固定几档，避免任意字号把 PTY 行列估乱。
 */
object TerminalFont {
	val steps: List<Int> = listOf(10, 12, 14, 16, 18)
	const val DEFAULT_SP = 12

	fun coerce(sp: Int): Int {
		if (sp in steps) return sp
		return steps.minWith(compareBy<Int> { kotlin.math.abs(it - sp) }.thenBy { it })
	}

	fun larger(sp: Int): Int {
		val current = coerce(sp)
		return steps.firstOrNull { it > current } ?: steps.last()
	}

	fun smaller(sp: Int): Int {
		val current = coerce(sp)
		return steps.lastOrNull { it < current } ?: steps.first()
	}
}
