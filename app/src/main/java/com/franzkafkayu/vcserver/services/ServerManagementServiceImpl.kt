package com.franzkafkayu.vcserver.services

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.repositories.ServerRepository
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 服务器管理服务实现
 */
class ServerManagementServiceImpl(
	private val serverRepository: ServerRepository,
	private val sshAuthenticationService: SshAuthenticationService,
	private val secureStorage: SecureStorage
) : ServerManagementService {
	override fun getAllServers(): Flow<List<Server>> {
		return serverRepository.getAllServers()
	}

	override suspend fun getServerById(id: Long): Server? {
		return serverRepository.getServerById(id)
	}

	override suspend fun addServer(
		name: String,
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String?,
		privateKey: String?,
		keyPassphrase: String?,
		proxyEnabled: Boolean,
		proxyType: com.franzkafkayu.vcserver.models.ProxyType?,
		proxyHost: String?,
		proxyPort: Int?,
		proxyUsername: String?,
		proxyPassword: String?,
		testConnection: Boolean
	): Result<Long> {
		// 输入验证
		val validationResult = validateServerInput(name, host, port, username, authType, password, privateKey)
		if (validationResult.isFailure) {
			return Result.failure(validationResult.exceptionOrNull() ?: ValidationException("Validation failed"))
		}

		// 如果是“仅测试连接”模式，不进行加密和入库，只做一次连接测试
		if (testConnection) {
			val testResult = sshAuthenticationService.testConnection(
				host = host,
				port = port,
				username = username,
				authType = authType,
				password = password,
				privateKey = privateKey,
				passphrase = keyPassphrase
			)
			return testResult.map { -1L } // 返回一个占位 ID，调用方并不关心具体值
		}

		// 下面是“真正保存服务器”逻辑：加密敏感信息 + 入库

		// 加密敏感信息
		val encryptedPassword = if (authType == AuthType.PASSWORD && password != null) {
			secureStorage.encryptPassword(password)
		} else {
			null
		}

		val encryptedPrivateKey = if (authType == AuthType.KEY && privateKey != null) {
			secureStorage.encryptPrivateKey(privateKey)
		} else {
			null
		}

		// 加密代理密码（如果启用代理）
		val encryptedProxyPassword = if (proxyEnabled && proxyPassword != null && proxyPassword.isNotBlank()) {
			secureStorage.encryptPassword(proxyPassword)
		} else {
			null
		}

		// 获取当前服务器数量，用于设置 orderIndex
		val currentServers = serverRepository.getAllServers().first()
		val serverCount = currentServers.size

		// 创建服务器实例
		val server = Server(
			name = name,
			host = host,
			port = port,
			username = username,
			authType = authType,
			encryptedPassword = encryptedPassword,
			encryptedPrivateKey = encryptedPrivateKey,
			keyPassphrase = keyPassphrase,
			proxyEnabled = proxyEnabled,
			proxyType = proxyType,
			proxyHost = proxyHost,
			proxyPort = proxyPort,
			proxyUsername = proxyUsername,
			encryptedProxyPassword = encryptedProxyPassword,
			orderIndex = serverCount // 新服务器默认排在最后
		)

		// 保存到数据库
		return try {
			val id = serverRepository.insertServer(server)
			Result.success(id)
		} catch (e: Exception) {
			// 如果保存失败，清理已加密的文件
			encryptedPassword?.let { secureStorage.deleteEncryptedFile(it) }
			encryptedPrivateKey?.let { secureStorage.deleteEncryptedFile(it) }
			encryptedProxyPassword?.let { secureStorage.deleteEncryptedFile(it) }
			Result.failure(e)
		}
	}

	override suspend fun updateServer(server: Server): Result<Unit> {
		return try {
			val updatedServer = server.copy(updatedAt = System.currentTimeMillis())
			serverRepository.updateServer(updatedServer)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	override suspend fun updateServer(
		serverId: Long,
		name: String,
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String?,
		privateKey: String?,
		keyPassphrase: String?,
		proxyEnabled: Boolean,
		proxyType: com.franzkafkayu.vcserver.models.ProxyType?,
		proxyHost: String?,
		proxyPort: Int?,
		proxyUsername: String?,
		proxyPassword: String?
	): Result<Unit> {
		return try {
			val existingServer = serverRepository.getServerById(serverId)
				?: return Result.failure(Exception("服务器不存在"))

			// 如果密码或私钥为空，保留原有的加密方式
			val encryptedPassword = when {
				authType == AuthType.PASSWORD && !password.isNullOrBlank() -> {
					// 删除旧的加密文件
					existingServer.encryptedPassword?.let { secureStorage.deleteEncryptedFile(it) }
					// 加密新密码
					secureStorage.encryptPassword(password)
				}
				authType == AuthType.PASSWORD -> existingServer.encryptedPassword
				else -> null
			}

			val encryptedPrivateKey = when {
				authType == AuthType.KEY && !privateKey.isNullOrBlank() -> {
					// 删除旧的加密文件
					existingServer.encryptedPrivateKey?.let { secureStorage.deleteEncryptedFile(it) }
					// 加密新私钥
					secureStorage.encryptPrivateKey(privateKey)
				}
				authType == AuthType.KEY -> existingServer.encryptedPrivateKey
				else -> null
			}

			// 处理代理密码加密
			val encryptedProxyPassword = when {
				proxyEnabled && !proxyPassword.isNullOrBlank() -> {
					// 删除旧的加密文件
					existingServer.encryptedProxyPassword?.let { secureStorage.deleteEncryptedFile(it) }
					// 加密新代理密码
					secureStorage.encryptPassword(proxyPassword)
				}
				proxyEnabled -> existingServer.encryptedProxyPassword
				else -> null
			}

			val updatedServer = existingServer.copy(
				name = name,
				host = host,
				port = port,
				username = username,
				authType = authType,
				encryptedPassword = encryptedPassword,
				encryptedPrivateKey = encryptedPrivateKey,
				keyPassphrase = if (authType == AuthType.KEY) keyPassphrase else null,
				proxyEnabled = proxyEnabled,
				proxyType = proxyType,
				proxyHost = proxyHost,
				proxyPort = proxyPort,
				proxyUsername = proxyUsername,
				encryptedProxyPassword = encryptedProxyPassword,
				updatedAt = System.currentTimeMillis()
			)

			serverRepository.updateServer(updatedServer)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	override suspend fun deleteServer(server: Server): Result<Unit> {
		return try {
			// 删除加密文件
			server.encryptedPassword?.let { secureStorage.deleteEncryptedFile(it) }
			server.encryptedPrivateKey?.let { secureStorage.deleteEncryptedFile(it) }
			server.encryptedProxyPassword?.let { secureStorage.deleteEncryptedFile(it) }
			
			// 从数据库删除
			serverRepository.deleteServer(server)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	override suspend fun testServerConnection(server: Server): Result<Unit> {
		// 解密敏感信息
		val password = server.encryptedPassword?.let { secureStorage.decryptPassword(it) }
		val privateKey = server.encryptedPrivateKey?.let { secureStorage.decryptPrivateKey(it) }

		return sshAuthenticationService.testConnection(
			host = server.host,
			port = server.port,
			username = server.username,
			authType = server.authType,
			password = password,
			privateKey = privateKey,
			passphrase = server.keyPassphrase
		)
	}

	override suspend fun updateServerOrder(servers: List<Server>): Result<Unit> {
		return try {
			// 更新每个服务器的 orderIndex
			val updatedServers = servers.mapIndexed { index, server ->
				server.copy(orderIndex = index, updatedAt = System.currentTimeMillis())
			}
			serverRepository.updateServers(updatedServers)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	/**
	 * 验证服务器输入
	 */
	private fun validateServerInput(
		name: String,
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String?,
		privateKey: String?
	): Result<Unit> {
		if (name.isBlank()) {
			return Result.failure(ValidationException("服务器名称不能为空"))
		}
		if (host.isBlank()) {
			return Result.failure(ValidationException("主机地址不能为空"))
		}
		if (port < 1 || port > 65535) {
			return Result.failure(ValidationException("端口号必须在 1-65535 之间"))
		}
		if (username.isBlank()) {
			return Result.failure(ValidationException("用户名不能为空"))
		}
		when (authType) {
			AuthType.PASSWORD -> {
				if (password.isNullOrBlank()) {
					return Result.failure(ValidationException("密码不能为空"))
				}
			}
			AuthType.KEY -> {
				if (privateKey.isNullOrBlank()) {
					return Result.failure(ValidationException("私钥不能为空"))
				}
			}
		}
		return Result.success(Unit)
	}
}

/**
 * 验证异常
 */
class ValidationException(message: String) : Exception(message)



