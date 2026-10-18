package com.franzkafkayu.vcserver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualizorMigrationTest {
	@Test
	fun copiesAccountOntoExistingLinks() {
		val account = VirtualizorLegacyAccount(
			host = "panel.example",
			port = 4083,
			encryptedApiKey = "key",
			encryptedApiPass = "pass",
			certPinSha256 = "pin"
		)
		val migrated = migrateVirtualizorLinksFromAccount(account, listOf(1L to "9", 2L to "10"))
		assertEquals(2, migrated.size)
		assertEquals("panel.example", migrated[0].host)
		assertEquals("9", migrated[0].vpsid)
		assertEquals("pin", migrated[1].certPinSha256)
	}

	@Test
	fun dropsLinksWhenAccountMissing() {
		val migrated = migrateVirtualizorLinksFromAccount(null, listOf(1L to "9"))
		assertTrue(migrated.isEmpty())
	}
}
