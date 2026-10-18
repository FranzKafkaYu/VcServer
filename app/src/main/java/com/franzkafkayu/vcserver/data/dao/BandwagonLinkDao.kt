package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.franzkafkayu.vcserver.models.BandwagonLink
import kotlinx.coroutines.flow.Flow

@Dao
interface BandwagonLinkDao {
	@Query("SELECT serverId FROM bandwagon_links")
	fun observeServerIds(): Flow<List<Long>>

	@Query("SELECT * FROM bandwagon_links WHERE serverId = :serverId")
	suspend fun getByServerId(serverId: Long): BandwagonLink?

	@Query("SELECT * FROM bandwagon_links WHERE veid = :veid")
	suspend fun getByVeid(veid: String): BandwagonLink?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(link: BandwagonLink): Long

	@Update
	suspend fun update(link: BandwagonLink)

	@Query("DELETE FROM bandwagon_links WHERE serverId = :serverId")
	suspend fun deleteByServerId(serverId: Long)
}
