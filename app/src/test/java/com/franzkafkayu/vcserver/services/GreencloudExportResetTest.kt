package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreencloudExportResetTest {
	@Test
	fun exportJsonOmitsGreencloudSecretsButKeepsServers() {
		val json = Gson().toJson(
			ExportData(
				groups = listOf(ServerGroup(id = 1, name = "组")),
				servers = listOf(
					Server(
						id = 7,
						name = "gc",
						host = "11.22.33.44",
						username = "root",
						authType = AuthType.PASSWORD
					)
				)
			)
		)
		assertTrue(json.contains("gc"))
		assertTrue(json.contains("组"))
		assertFalse(json.contains("encryptedApiToken"))
		assertFalse(json.contains("apiToken"))
		assertFalse(json.contains("YOUR_SECRET_TOKEN"))
	}

	@Test
	fun settingsResetDoesNotDeleteGreencloudLinks() {
		assertFalse(SettingsServiceImpl.RESET_DELETES_GREENCLOUD_LINKS)
	}
}
