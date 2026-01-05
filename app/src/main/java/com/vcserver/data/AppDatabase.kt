package com.vcserver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vcserver.data.dao.ServerDao
import com.vcserver.data.converters.AuthTypeConverter
import com.vcserver.models.Server

/**
 * 应用数据库
 */
@Database(
	entities = [Server::class],
	version = 2,
	exportSchema = false
)
@TypeConverters(AuthTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun serverDao(): ServerDao
}



