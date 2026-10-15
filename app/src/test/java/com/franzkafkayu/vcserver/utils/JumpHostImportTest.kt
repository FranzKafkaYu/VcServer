package com.franzkafkayu.vcserver.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpHostImportTest {
	private val rows = listOf(
		JumpImportRow(exportId = 10, host = "bastion", port = 22, localId = 3, jumpExportId = null, written = true),
		JumpImportRow(exportId = 20, host = "app", port = 22, localId = 8, jumpExportId = 10, written = true)
	)

	@Test
	fun remapJumpByExportId() {
		assertEquals(3L, resolveImportedJumpHostId(rows, 10))
	}

	@Test
	fun unmatchedJumpBecomesDirect() {
		assertNull(resolveImportedJumpHostId(rows, 99))
	}

	@Test
	fun missingJumpFieldStaysDirect() {
		assertNull(resolveImportedJumpHostId(rows, null))
	}
}
