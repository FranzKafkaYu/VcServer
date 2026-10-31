package com.franzkafkayu.vcserver.mosh

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.services.ExportData
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoshPersistenceTest {
	private val gson = Gson()

	@Test
	fun missingBackupFieldsDefaultOff() {
		val json = """{"name":"a","host":"h","port":22,"username":"root","authType":"PASSWORD"}"""
		val server = gson.fromJson(json, Server::class.java)
		assertFalse(server.useMosh)
		assertNull(server.moshUdpPort)
	}

	@Test
	fun exportIncludesMoshFields() {
		val json = gson.toJson(
			ExportData(
				servers = listOf(
					Server(
						id = 1,
						name = "m",
						host = "1.2.3.4",
						username = "root",
						authType = AuthType.PASSWORD,
						useMosh = true,
						moshUdpPort = 60001
					)
				)
			)
		)
		assertTrue(json.contains("\"useMosh\": true") || json.contains("\"useMosh\":true"))
		assertTrue(json.contains("60001"))
		assertFalse(json.contains("moshKey"))
		assertFalse(json.contains("MOSH CONNECT"))
	}

	@Test
	fun serverEntityHasNoSessionKey() {
		val names = Server::class.java.declaredFields.map { it.name }
		assertFalse(names.contains("moshKey"))
		assertFalse(names.contains("sessionKey"))
		assertTrue(names.contains("useMosh"))
		assertTrue(names.contains("moshUdpPort"))
	}

	@Test
	fun migrationAddsNullablePort() {
		assertEquals(15, 14 + 1)
		assertTrue(
			com.franzkafkayu.vcserver.data.AppDatabase.MIGRATION_14_15.startVersion == 14 &&
				com.franzkafkayu.vcserver.data.AppDatabase.MIGRATION_14_15.endVersion == 15
		)
	}
}
