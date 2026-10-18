package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.franzkafkayu.vcserver.models.VirtualizorLink
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualizorLinkDao {
	@Query("SELECT * FROM virtualizor_links")
	fun observeAll(): Flow<List<VirtualizorLink>>

	@Query("SELECT * FROM virtualizor_links")
	suspend fun all(): List<VirtualizorLink>

	@Query("SELECT * FROM virtualizor_links WHERE serverId = :serverId")
	suspend fun getByServerId(serverId: Long): VirtualizorLink?

	@Query("SELECT * FROM virtualizor_links WHERE host = :host AND port = :port AND vpsid = :vpsid")
	suspend fun getByPanelVps(host: String, port: Int, vpsid: String): VirtualizorLink?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(link: VirtualizorLink): Long

	@Query("DELETE FROM virtualizor_links WHERE serverId = :serverId")
	suspend fun deleteByServerId(serverId: Long)

	@Query("DELETE FROM virtualizor_links")
	suspend fun deleteAll()
}
