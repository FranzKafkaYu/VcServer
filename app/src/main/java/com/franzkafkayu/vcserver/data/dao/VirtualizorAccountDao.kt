package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.franzkafkayu.vcserver.models.VirtualizorAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualizorAccountDao {
	@Query("SELECT * FROM virtualizor_account WHERE id = 1")
	fun observe(): Flow<VirtualizorAccount?>

	@Query("SELECT * FROM virtualizor_account WHERE id = 1")
	suspend fun get(): VirtualizorAccount?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(account: VirtualizorAccount)

	@Query("DELETE FROM virtualizor_account")
	suspend fun delete()
}
