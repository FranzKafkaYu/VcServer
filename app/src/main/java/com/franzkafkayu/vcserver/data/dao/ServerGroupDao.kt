package com.franzkafkayu.vcserver.data.dao

import androidx.room.*
import com.franzkafkayu.vcserver.models.ServerGroup
import kotlinx.coroutines.flow.Flow

/**
 * 服务器分组数据访问对象
 */
@Dao
interface ServerGroupDao {
	/**
	 * 获取所有分组列表
	 */
	@Query("SELECT * FROM server_groups ORDER BY name ASC")
	fun getAllGroups(): Flow<List<ServerGroup>>

	/**
	 * 根据 ID 获取分组
	 */
	@Query("SELECT * FROM server_groups WHERE id = :id")
	suspend fun getGroupById(id: Long): ServerGroup?

	/**
	 * 根据名称获取分组
	 */
	@Query("SELECT * FROM server_groups WHERE name = :name")
	suspend fun getGroupByName(name: String): ServerGroup?

	/**
	 * 插入分组
	 */
	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insertGroup(group: ServerGroup): Long

	/**
	 * 更新分组
	 */
	@Update
	suspend fun updateGroup(group: ServerGroup)

	/**
	 * 删除分组
	 */
	@Delete
	suspend fun deleteGroup(group: ServerGroup)
}
