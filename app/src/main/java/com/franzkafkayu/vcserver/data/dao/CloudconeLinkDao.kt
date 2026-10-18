package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.franzkafkayu.vcserver.models.CloudconeLink
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudconeLinkDao {
	@Query("SELECT * FROM cloudcone_links")
	fun observeAll(): Flow<List<CloudconeLink>>

	@Query("SELECT * FROM cloudcone_links")
	suspend fun all(): List<CloudconeLink>

	@Query("SELECT * FROM cloudcone_links WHERE serverId = :serverId")
	suspend fun getByServerId(serverId: Long): CloudconeLink?

	@Query("SELECT * FROM cloudcone_links WHERE instanceId = :instanceId")
	suspend fun getByInstanceId(instanceId: String): CloudconeLink?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(link: CloudconeLink): Long

	@Query("DELETE FROM cloudcone_links WHERE serverId = :serverId")
	suspend fun deleteByServerId(serverId: Long)

	@Query("DELETE FROM cloudcone_links")
	suspend fun deleteAll()
}
