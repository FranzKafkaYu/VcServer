package com.vcserver.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 服务器实体类
 */
@Entity(tableName = "servers")
data class Server(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val name: String,
	val host: String,
	val port: Int = 22,
	val username: String,
	val authType: AuthType,
	val encryptedPassword: String? = null,  // 加密后的密码（authType 为 PASSWORD 时使用）
	val encryptedPrivateKey: String? = null, // 加密后的私钥（authType 为 KEY 时使用）
	val keyPassphrase: String? = null,       // 密钥密码（可选，如果私钥有密码保护）
	val createdAt: Long = System.currentTimeMillis(),
	val updatedAt: Long = System.currentTimeMillis()
)



