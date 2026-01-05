package com.franzkafkayu.vcserver.services

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.utils.SecureStorage
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
		private const val TAG = "SshAuthenticationService"
		private const val CONNECTION_TIMEOUT = 5000 // 5秒，进一步减少超时
		
		/**
		 * 优化的 SSH 配置，加快连接速度
		 * 这些配置针对 JSch 库优化，减少连接建立时的延迟
		 */
		private fun getOptimizedConfig(): Properties {
			val config = Properties()
			// 禁用主机密钥检查（避免等待用户确认）
			config["StrictHostKeyChecking"] = "no"
			config["UserKnownHostsFile"] = "/dev/null"
			
			// 优化认证顺序，优先使用密码认证（如果使用密码）
			config["PreferredAuthentications"] = "password,publickey,keyboard-interactive"
			
			// 禁用 GSSAPI 认证（避免额外的认证协商时间）
			config["GSSAPIAuthentication"] = "no"
			config["GSSAPIDelegateCredentials"] = "no"
			
			// 禁用压缩（对于快速网络，压缩可能增加 CPU 开销）
			config["Compression"] = "no"
			
			// 设置服务器存活间隔（保持连接活跃，避免超时重连）
			config["ServerAliveInterval"] = "30"
			config["ServerAliveCountMax"] = "3"
			
			// 禁用 DNS 查找（如果使用 IP 地址）
			config["CheckHostIP"] = "no"
			
			return config
		}
	}

	override suspend fun connectWithPassword(
		host: String,
		port: Int,
		username: String,
		password: String
	): Result<Session> = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		try {
			val jsch = JSch()
			val session = jsch.getSession(username, host, port)
			session.setPassword(password)
			
			// 使用优化的 SSH 配置
			session.setConfig(getOptimizedConfig())
			
			session.connect(CONNECTION_TIMEOUT)
			val duration = System.currentTimeMillis() - startTime
			Log.i(TAG, "connectWithPassword 连接成功: $host:$port, 耗时: ${duration}ms")
			Result.success(session)
		} catch (e: Exception) {
			val duration = System.currentTimeMillis() - startTime
			Log.i(TAG, "connectWithPassword 连接失败: $host:$port, 耗时: ${duration}ms, 错误: ${e.message}")
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
			
			// 使用优化的 SSH 配置
			session.setConfig(getOptimizedConfig())
			
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



