package com.franzkafkayu.vcserver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudconeMigrationTest {
	@Test
	fun copiesAccountOntoExistingLinks() {
		val account = CloudconeLegacyAccount(encryptedApiKey = "key", encryptedHash = "hash")
		val migrated = migrateCloudconeLinksFromAccount(account, listOf(1L to "9", 2L to "10"))
		assertEquals(2, migrated.size)
		assertEquals("9", migrated[0].instanceId)
		assertEquals("key", migrated[0].encryptedApiKey)
		assertEquals("hash", migrated[1].encryptedHash)
	}

	@Test
	fun dropsLinksWhenAccountMissing() {
		val migrated = migrateCloudconeLinksFromAccount(null, listOf(1L to "9"))
		assertTrue(migrated.isEmpty())
	}
}
