package com.franzkafkayu.vcserver.services

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * SSH 认证服务接口
 */
interface SshAuthenticationService {
	/**
	 * 使用密码进行 SSH 连接
	 */
	suspend fun connectWithPassword(
		host: String,
		port: Int,
		username: String,
		password: String
	): Result<Session>

	/**
	 * 使用密钥进行 SSH 连接
	 */
	suspend fun connectWithKey(
		host: String,
		port: Int,
		username: String,
		privateKey: String,
		passphrase: String? = null
	): Result<Session>

	/**
	 * 测试连接（连接后立即关闭�?
	 */
	suspend fun testConnection(
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String? = null,
		privateKey: String? = null,
		passphrase: String? = null
	): Result<Unit>

	/**
	 * 关闭 SSH 连接
	 */
	fun closeSession(session: Session)
}



