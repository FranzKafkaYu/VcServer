package com.franzkafkayu.vcserver.repositories

import com.franzkafkayu.vcserver.data.dao.ServerDao
import com.franzkafkayu.vcserver.data.dao.ServerGroupDao
import com.franzkafkayu.vcserver.models.ServerGroup
import kotlinx.coroutines.flow.Flow

/**
 * 服务器分组仓库实现
 */
class ServerGroupRepositoryImpl(
	private val serverGroupDao: ServerGroupDao,
	private val serverDao: ServerDao
) : ServerGroupRepository {
	override fun getAllGroups(): Flow<List<ServerGroup>> {
		return serverGroupDao.getAllGroups()
	}

	override suspend fun getGroupById(id: Long): ServerGroup? {
		return serverGroupDao.getGroupById(id)
	}

	override suspend fun getGroupByName(name: String): ServerGroup? {
		return serverGroupDao.getGroupByName(name)
	}

		override suspend fun createGroup(name: String): Result<ServerGroup> {
		return try {
			val trimmedName = name.trim()
			
			// 验证名称非空
			if (trimmedName.isEmpty()) {
				return Result.failure(Exception("GROUP_NAME_EMPTY"))
			}
			
			// 验证名称唯一性（不区分大小写）
			val existingGroup = serverGroupDao.getGroupByName(trimmedName)
			if (existingGroup != null) {
				return Result.failure(Exception("GROUP_NAME_EXISTS"))
			}

			// 创建分组
			val now = System.currentTimeMillis()
			val group = ServerGroup(
				name = trimmedName,
				createdAt = now,
				updatedAt = now
			)
			val id = serverGroupDao.insertGroup(group)
			val createdGroup = group.copy(id = id)
			Result.success(createdGroup)
		} catch (e: Exception) {
			// 处理唯一性约束违反
			if (e.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ||
				e.message?.contains("GROUP_NAME_EXISTS") == true
			) {
				Result.failure(Exception("GROUP_NAME_EXISTS"))
			} else {
				Result.failure(e)
			}
		}
	}

		override suspend fun updateGroup(group: ServerGroup): Result<Unit> {
		return try {
			// 验证名称唯一性（排除自身）
			val existingGroup = serverGroupDao.getGroupByName(group.name.trim())
			if (existingGroup != null && existingGroup.id != group.id) {
				return Result.failure(Exception("GROUP_NAME_EXISTS"))
			}

			// 更新分组
			val updatedGroup = group.copy(
				name = group.name.trim(),
				updatedAt = System.currentTimeMillis()
			)
			serverGroupDao.updateGroup(updatedGroup)
			Result.success(Unit)
		} catch (e: Exception) {
			// 处理唯一性约束违反
			if (e.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true ||
				e.message?.contains("GROUP_NAME_EXISTS") == true
			) {
				Result.failure(Exception("GROUP_NAME_EXISTS"))
			} else {
				Result.failure(e)
			}
		}
	}

	override suspend fun deleteGroup(groupId: Long): Result<Unit> {
		return try {
			val group = serverGroupDao.getGroupById(groupId)
				?: return Result.failure(Exception("分组不存在"))

			// 将属于该分组的服务器设为未分组
			serverDao.ungroupServersByGroupId(groupId)

			// 删除分组
			serverGroupDao.deleteGroup(group)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}
}
