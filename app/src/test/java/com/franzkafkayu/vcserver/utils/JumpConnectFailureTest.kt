package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpConnectFailureTest {
	@Test
	fun jumpFailureIsNotLabeledAsTargetFailure() {
		val code = JumpConnectFailure.code(jumpSide = true, Exception("Auth fail"))
		assertEquals(JumpConnectFailure.JUMP_FAILED, code)
		assertTrue(JumpConnectFailure.isJumpSide(code))
		assertFalse(JumpConnectFailure.isJumpSide(JumpConnectFailure.TARGET_FAILED))
	}

	@Test
	fun algorithmOnJumpStaysOnJumpSide() {
		val error = Exception("SSH_ALGORITHM_NEGOTIATION_FAILED")
		assertEquals(
			JumpConnectFailure.JUMP_ALGORITHM,
			JumpConnectFailure.code(jumpSide = true, error)
		)
	}

	@Test
	fun algorithmOnTargetKeepsExistingCode() {
		val error = Exception("Algorithm negotiation fail")
		assertEquals(
			"SSH_ALGORITHM_NEGOTIATION_FAILED",
			JumpConnectFailure.code(jumpSide = false, error)
		)
	}
}
