package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "virtualizor_links",
	indices = [
		Index(value = ["serverId"], unique = true),
		Index(value = ["host", "port", "vpsid"], unique = true)
	]
)
data class VirtualizorLink(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val serverId: Long,
	val vpsid: String,
	val host: String,
	val port: Int,
	val encryptedApiKey: String,
	val encryptedApiPass: String,
	val certPinSha256: String? = null
) {
	companion object {
		const val DEFAULT_PORT = 4083
	}
}
