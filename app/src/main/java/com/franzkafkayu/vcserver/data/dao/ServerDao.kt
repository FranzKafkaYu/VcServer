package com.franzkafkayu.vcserver.data.dao

import androidx.room.*
import com.franzkafkayu.vcserver.models.Server
import kotlinx.coroutines.flow.Flow

/**
 * 服务器数据访问对象
 */
@Dao
interface ServerDao {
	/**
	 * 获取所有服务器列表
	 */
	@Query("SELECT * FROM servers ORDER BY orderIndex ASC, createdAt DESC")
	fun getAllServers(): Flow<List<Server>>

	/**
	 * 根据分组ID获取服务器列表
	 */
	@Query("SELECT * FROM servers WHERE groupId = :groupId ORDER BY orderIndex ASC, createdAt DESC")
	suspend fun getServersByGroupId(groupId: Long): List<Server>

	/**
	 * 获取未分组的服务器列表
	 */
	@Query("SELECT * FROM servers WHERE groupId IS NULL ORDER BY orderIndex ASC, createdAt DESC")
	suspend fun getServersWithoutGroup(): List<Server>

	/**
	 * 将指定分组的所有服务器设为未分组
	 */
	@Query("UPDATE servers SET groupId = NULL WHERE groupId = :groupId")
	suspend fun ungroupServersByGroupId(groupId: Long)

	/**
	 * 根据 ID 获取服务器
	 */
	@Query("SELECT * FROM servers WHERE id = :id")
	suspend fun getServerById(id: Long): Server?

	/**
	 * 插入服务器
	 */
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertServer(server: Server): Long

	/**
	 * 更新服务器
	 */
	@Update
	suspend fun updateServer(server: Server)

	/**
	 * 删除服务器
	 */
	@Delete
	suspend fun deleteServer(server: Server)

	/**
	 * 批量更新服务器
	 */
	@Update
	suspend fun updateServers(servers: List<Server>)
}



