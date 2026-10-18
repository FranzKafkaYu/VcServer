package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudconeExportResetTest {
	@Test
	fun exportJsonOmitsCloudconeSecretsButKeepsServers() {
		val json = Gson().toJson(
			ExportData(
				groups = listOf(ServerGroup(id = 1, name = "组")),
				servers = listOf(
					Server(
						id = 7,
						name = "cone",
						host = "11.22.33.44",
						username = "root",
						authType = AuthType.PASSWORD
					)
				)
			)
		)
		assertTrue(json.contains("cone"))
		assertTrue(json.contains("组"))
		assertFalse(json.contains("App-Secret"))
		assertFalse(json.contains("encryptedHash"))
		assertFalse(json.contains("encryptedApiKey"))
		assertFalse(json.contains("apiKey"))
	}

	@Test
	fun settingsResetDoesNotDeleteCloudconeAccount() {
		assertFalse(SettingsServiceImpl.RESET_DELETES_CLOUDCONE_ACCOUNT)
	}
}
