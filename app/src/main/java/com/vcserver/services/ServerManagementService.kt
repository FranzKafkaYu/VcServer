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
	 * 更新服务器（支持部分字段更新，保留未修改的敏感信息）
	 * @param serverId 服务器ID
	 * @param name 服务器名称
	 * @param host 主机地址
	 * @param port 端口
	 * @param username 用户名
	 * @param authType 认证类型
	 * @param password 新密码（如果为null或空，保留原有密码）
	 * @param privateKey 新私钥（如果为null或空，保留原有私钥）
	 * @param keyPassphrase 密钥密码
	 */
	suspend fun updateServer(
		serverId: Long,
		name: String,
		host: String,
		port: Int,
		username: String,
		authType: com.vcserver.models.AuthType,
		password: String? = null,
		privateKey: String? = null,
		keyPassphrase: String? = null
	): Result<Unit>

	/**
	 * 删除服务器
	 */
	suspend fun deleteServer(server: Server): Result<Unit>

	/**
	 * 测试服务器连接
	 */
	suspend fun testServerConnection(server: Server): Result<Unit>

	/**
	 * 更新服务器排序顺序
	 * @param servers 按新顺序排列的服务器列表
	 */
	suspend fun updateServerOrder(servers: List<Server>): Result<Unit>
}



