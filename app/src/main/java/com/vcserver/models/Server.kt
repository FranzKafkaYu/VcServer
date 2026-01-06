package com.vcserver.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.vcserver.data.converters.ProxyTypeConverter
import java.util.Date

/**
 * 服务器实体类
 */
@Entity(tableName = "servers")
@TypeConverters(ProxyTypeConverter::class)
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
	val systemVersion: String? = null,       // 系统版本信息（例如 "Ubuntu 22.04"），连接后自动更新
	val orderIndex: Int = 0,                 // 排序索引，用于用户自定义排序
	// 代理设置（服务器级别）
	val proxyEnabled: Boolean = false,
	val proxyType: ProxyType? = null,
	val proxyHost: String? = null,
	val proxyPort: Int? = null,
	val proxyUsername: String? = null,
	val encryptedProxyPassword: String? = null, // 加密后的代理密码
	val createdAt: Long = System.currentTimeMillis(),
	val updatedAt: Long = System.currentTimeMillis()
)



