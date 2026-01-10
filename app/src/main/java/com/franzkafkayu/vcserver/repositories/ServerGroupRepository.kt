package com.franzkafkayu.vcserver.repositories

import com.franzkafkayu.vcserver.models.ServerGroup
import kotlinx.coroutines.flow.Flow

/**
 * 服务器分组仓库接口
 */
interface ServerGroupRepository {
	/**
	 * 获取所有分组列表
	 */
	fun getAllGroups(): Flow<List<ServerGroup>>

	/**
	 * 根据 ID 获取分组
	 */
	suspend fun getGroupById(id: Long): ServerGroup?

	/**
	 * 根据名称获取分组
	 */
	suspend fun getGroupByName(name: String): ServerGroup?

	/**
	 * 创建分组（验证名称唯一性）
	 */
	suspend fun createGroup(name: String): Result<ServerGroup>

	/**
	 * 更新分组（验证名称唯一性，排除自身）
	 */
	suspend fun updateGroup(group: ServerGroup): Result<Unit>

	/**
	 * 删除分组（将关联服务器设为未分组）
	 */
	suspend fun deleteGroup(groupId: Long): Result<Unit>
}
