package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 搬瓦工实例与已保存服务器的绑定。API key 只存加密文件路径。
 */
@Entity(
	tableName = "bandwagon_links",
	indices = [
		Index(value = ["serverId"], unique = true),
		Index(value = ["veid"], unique = true)
	]
)
data class BandwagonLink(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val serverId: Long,
	val veid: String,
	val encryptedApiKey: String
)
