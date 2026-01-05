package com.vcserver.services

import com.vcserver.models.Server
import com.vcserver.repositories.ServerRepository
import com.vcserver.utils.SecureStorage
import kotlinx.coroutines.flow.Flow

/**
 * 服务器管理服务接口
 */
interface ServerManagementService {
	/**
	 * 获取所有服务器列表
	 */
	fun getAllServers(): Flow<List<Server>>

	/**
	 * 根据 ID 获取服务器
	 */
	suspend fun getServerById(id: Long): Server?

	/**
	 * 添加服务器（包含输入验证、加密存储、连接测试）
	 */
	suspend fun addServer(
		name: String,
		host: String,
		port: Int,
		username: String,
		authType: com.vcserver.models.AuthType,
		password: String? = null,
		privateKey: String? = null,
		keyPassphrase: String? = null,
		testConnection: Boolean = false
	): Result<Long>

	/**
	 * 更新服务器
	 */
	suspend fun updateServer(server: Server): Result<Unit>

	/**
	 * 删除服务器
	 */
	suspend fun deleteServer(server: Server): Result<Unit>

	/**
	 * 测试服务器连接
	 */
	suspend fun testServerConnection(server: Server): Result<Unit>
}



