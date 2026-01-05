package com.vcserver.services

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.vcserver.models.AuthType
import com.vcserver.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Properties

/**
 * SSH 认证服务实现
 */
class SshAuthenticationServiceImpl(
	private val secureStorage: SecureStorage
) : SshAuthenticationService {
	companion object {
		private const val CONNECTION_TIMEOUT = 30000 // 30 秒
	}

	override suspend fun connectWithPassword(
		host: String,
		port: Int,
		username: String,
		password: String
	): Result<Session> = withContext(Dispatchers.IO) {
		try {
			val jsch = JSch()
			val session = jsch.getSession(username, host, port)
			session.setPassword(password)
			
			// 配置 SSH 连接属性
			val config = Properties()
			config["StrictHostKeyChecking"] = "no"
			session.setConfig(config)
			
			session.connect(CONNECTION_TIMEOUT)
			Result.success(session)
		} catch (e: Exception) {
			Result.failure(SshConnectionException("Failed to connect with password: ${e.message}", e))
		}
	}

	override suspend fun connectWithKey(
		host: String,
		port: Int,
		username: String,
		privateKey: String,
		passphrase: String?
	): Result<Session> = withContext(Dispatchers.IO) {
		try {
			val jsch = JSch()
			
			// 添加私钥
			if (passphrase != null && passphrase.isNotEmpty()) {
				jsch.addIdentity("key", privateKey.toByteArray(), null, passphrase.toByteArray())
			} else {
				jsch.addIdentity("key", privateKey.toByteArray(), null, null)
			}
			
			val session = jsch.getSession(username, host, port)
			
			// 配置 SSH 连接属性
			val config = Properties()
			config["StrictHostKeyChecking"] = "no"
			session.setConfig(config)
			
			session.connect(CONNECTION_TIMEOUT)
			Result.success(session)
		} catch (e: Exception) {
			Result.failure(SshConnectionException("Failed to connect with key: ${e.message}", e))
		}
	}

	override suspend fun testConnection(
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String?,
		privateKey: String?,
		passphrase: String?
	): Result<Unit> = withContext(Dispatchers.IO) {
		val result: Result<Session> = when (authType) {
			AuthType.PASSWORD -> {
				if (password == null) {
					Result.failure(SshConnectionException("Password is required"))
				} else {
					connectWithPassword(host, port, username, password)
				}
			}
			AuthType.KEY -> {
				if (privateKey == null) {
					Result.failure(SshConnectionException("Private key is required"))
				} else {
					connectWithKey(host, port, username, privateKey, passphrase)
				}
			}
		}

		result.fold(
			onSuccess = { session: Session ->
				closeSession(session)
				Result.success(Unit)
			},
			onFailure = { exception ->
				Result.failure(exception)
			}
		)
	}

	override fun closeSession(session: Session) {
		try {
			if (session.isConnected) {
				session.disconnect()
			}
		} catch (e: Exception) {
			// 忽略关闭错误
		}
	}
}

/**
 * SSH 连接异常
 */
class SshConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)



