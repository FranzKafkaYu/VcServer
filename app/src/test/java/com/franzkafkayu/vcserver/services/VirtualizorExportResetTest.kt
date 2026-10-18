package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualizorExportResetTest {
	@Test
	fun exportJsonOmitsVirtualizorSecretsButKeepsServers() {
		val json = Gson().toJson(
			ExportData(
				groups = listOf(ServerGroup(id = 1, name = "组")),
				servers = listOf(
					Server(
						id = 7,
						name = "vz",
						host = "11.22.33.44",
						username = "root",
						authType = AuthType.PASSWORD
					)
				)
			)
		)
		assertTrue(json.contains("vz"))
		assertTrue(json.contains("组"))
		assertFalse(json.contains("apipass"))
		assertFalse(json.contains("apiPass"))
		assertFalse(json.contains("encryptedApiPass"))
		assertFalse(json.contains("encryptedApiKey"))
		assertFalse(json.contains("apiKey"))
	}

	@Test
	fun settingsResetDoesNotDeleteVirtualizorAccount() {
		assertFalse(SettingsServiceImpl.RESET_DELETES_VIRTUALIZOR_ACCOUNT)
	}
}
