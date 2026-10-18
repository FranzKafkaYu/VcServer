package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BandwagonExportTest {
	@Test
	fun exportJsonOmitsBandwagonCredentialsButKeepsServersAndGroups() {
		val json = Gson().toJson(
			ExportData(
				groups = listOf(ServerGroup(id = 1, name = "美国")),
				servers = listOf(
					Server(
						id = 7,
						name = "bwg",
						host = "11.22.33.44",
						username = "root",
						authType = AuthType.PASSWORD
					)
				)
			)
		)
		assertTrue(json.contains("bwg"))
		assertTrue(json.contains("美国"))
		assertFalse(json.contains("veid"))
		assertFalse(json.contains("api_key"))
		assertFalse(json.contains("apiKey"))
		assertFalse(json.contains("encryptedApiKey"))
	}
}
