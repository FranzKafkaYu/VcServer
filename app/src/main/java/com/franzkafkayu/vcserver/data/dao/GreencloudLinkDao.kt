package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.franzkafkayu.vcserver.models.GreencloudLink
import kotlinx.coroutines.flow.Flow

@Dao
interface GreencloudLinkDao {
	@Query("SELECT * FROM greencloud_links")
	fun observeAll(): Flow<List<GreencloudLink>>

	@Query("SELECT * FROM greencloud_links")
	suspend fun all(): List<GreencloudLink>

	@Query("SELECT * FROM greencloud_links WHERE serverId = :serverId")
	suspend fun getByServerId(serverId: Long): GreencloudLink?

	@Query("SELECT * FROM greencloud_links WHERE instanceId = :instanceId")
	suspend fun getByInstanceId(instanceId: String): GreencloudLink?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(link: GreencloudLink): Long

	@Query("DELETE FROM greencloud_links WHERE serverId = :serverId")
	suspend fun deleteByServerId(serverId: Long)

	@Query("DELETE FROM greencloud_links")
	suspend fun deleteAll()
}
