package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CloudCone 账号。同时只保留一行，密钥只存加密文件路径。
 */
@Entity(tableName = "cloudcone_account")
data class CloudconeAccount(
	@PrimaryKey val id: Long = SINGLE_ID,
	val encryptedApiKey: String,
	val encryptedHash: String
) {
	companion object {
		const val SINGLE_ID = 1L
	}
}
