package com.vcserver.data.dao

import androidx.room.*
import com.vcserver.models.Server
import kotlinx.coroutines.flow.Flow

/**
 * 服务器数据访问对象
 */
@Dao
interface ServerDao {
	/**
	 * 获取所有服务器列表
	 */
	@Query("SELECT * FROM servers ORDER BY createdAt DESC")
	fun getAllServers(): Flow<List<Server>>

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
}



