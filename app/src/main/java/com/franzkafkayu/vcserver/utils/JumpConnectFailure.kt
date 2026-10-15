package com.franzkafkayu.vcserver.utils

/**
 * 把跳板一侧和目标一侧的连接失败分成不同错误码。
 */
object JumpConnectFailure {
	const val MISSING = "JUMP_HOST_MISSING"
	const val JUMP_FAILED = "JUMP_HOST_FAILED"
	const val JUMP_ALGORITHM = "JUMP_ALGORITHM_NEGOTIATION_FAILED"
	const val TARGET_FAILED = "TARGET_CONNECT_FAILED"

	fun code(jumpSide: Boolean, error: Throwable): String {
		val algorithm = isAlgorithmNegotiation(error)
		return when {
			jumpSide && algorithm -> JUMP_ALGORITHM
			jumpSide -> JUMP_FAILED
			algorithm -> "SSH_ALGORITHM_NEGOTIATION_FAILED"
			else -> TARGET_FAILED
		}
	}

	fun isJumpSide(code: String): Boolean {
		return code == MISSING || code == JUMP_FAILED || code == JUMP_ALGORITHM
	}

	private fun isAlgorithmNegotiation(error: Throwable): Boolean {
		var current: Throwable? = error
		while (current != null) {
			val message = current.message.orEmpty()
			if (
				message == "SSH_ALGORITHM_NEGOTIATION_FAILED" ||
				message.contains("Algorithm negotiation fail", ignoreCase = true) ||
				message.contains("no matching host key", ignoreCase = true) ||
				message.contains("no matching key exchange", ignoreCase = true)
			) {
				return true
			}
			current = current.cause
		}
		return false
	}
}
