package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Virtualizor 面板账号。同时只保留一行，密钥只存加密文件路径。
 */
@Entity(tableName = "virtualizor_account")
data class VirtualizorAccount(
	@PrimaryKey val id: Long = SINGLE_ID,
	val host: String,
	val port: Int,
	val encryptedApiKey: String,
	val encryptedApiPass: String,
	val certPinSha256: String? = null
) {
	companion object {
		const val SINGLE_ID = 1L
		const val DEFAULT_PORT = 4083
	}
}
