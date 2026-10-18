package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CloudCone 实例与 SSH 服务器的绑定。密钥在账号上，不在这一行。
 */
@Entity(
	tableName = "cloudcone_links",
	indices = [
		Index(value = ["serverId"], unique = true),
		Index(value = ["instanceId"], unique = true)
	]
)
data class CloudconeLink(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val serverId: Long,
	val instanceId: String
)
