package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.ssh.SshClientSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SSH 认证服务接口
 */
interface SshAuthenticationService {
	/**
	 * 使用密码进行 SSH 连接。jump 非空时经该跳板的 direct-tcpip 握手。
	 */
	suspend fun connectWithPassword(
		host: String,
		port: Int,
		username: String,
		password: String,
		jump: SshClientSession? = null
	): Result<SshClientSession>

	/**
	 * 使用密钥进行 SSH 连接。jump 非空时经该跳板的 direct-tcpip 握手。
	 */
	suspend fun connectWithKey(
		host: String,
		port: Int,
		username: String,
		privateKey: String,
		passphrase: String? = null,
		jump: SshClientSession? = null
	): Result<SshClientSession>

	/**
	 * 测试连接（连接后立即关闭）
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
	fun closeSession(session: SshClientSession)
}
