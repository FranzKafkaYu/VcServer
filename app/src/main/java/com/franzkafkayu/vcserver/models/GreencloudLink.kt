package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "greencloud_links",
	indices = [
		Index(value = ["serverId"], unique = true),
		Index(value = ["instanceId"], unique = true)
	]
)
data class GreencloudLink(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val serverId: Long,
	val instanceId: String,
	val encryptedApiToken: String
)
