package com.franzkafkayu.vcserver.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.franzkafkayu.vcserver.models.CloudconeAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudconeAccountDao {
	@Query("SELECT * FROM cloudcone_account WHERE id = 1")
	fun observe(): Flow<CloudconeAccount?>

	@Query("SELECT * FROM cloudcone_account WHERE id = 1")
	suspend fun get(): CloudconeAccount?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(account: CloudconeAccount)

	@Query("DELETE FROM cloudcone_account")
	suspend fun delete()
}
