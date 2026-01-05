package com.vcserver.repositories

import com.vcserver.models.Server
import kotlinx.coroutines.flow.Flow

/**
 * 服务器仓库接口
 */
interface ServerRepository {
	/**
	 * 获取所有服务器列表
	 */
	fun getAllServers(): Flow<List<Server>>

	/**
	 * 根据 ID 获取服务器
	 */
	suspend fun getServerById(id: Long): Server?

	/**
	 * 添加服务器
	 */
	suspend fun insertServer(server: Server): Long

	/**
	 * 更新服务器
	 */
	suspend fun updateServer(server: Server)

	/**
	 * 删除服务器
	 */
	suspend fun deleteServer(server: Server)
}



