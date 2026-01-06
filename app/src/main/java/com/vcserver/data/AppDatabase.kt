package com.vcserver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vcserver.data.dao.ServerDao
import com.vcserver.data.converters.AuthTypeConverter
import com.vcserver.data.converters.ProxyTypeConverter
import com.vcserver.models.Server

/**
 * 应用数据库
 */
@Database(
	entities = [Server::class],
	version = 4,
	exportSchema = false
)
@TypeConverters(AuthTypeConverter::class, ProxyTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun serverDao(): ServerDao

	companion object {
		/**
		 * 数据库迁移：从版本 3 到版本 4
		 * 添加代理相关字段
		 */
		val MIGRATION_3_4 = object : Migration(3, 4) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE servers ADD COLUMN proxyEnabled INTEGER NOT NULL DEFAULT 0")
				database.execSQL("ALTER TABLE servers ADD COLUMN proxyType TEXT")
				database.execSQL("ALTER TABLE servers ADD COLUMN proxyHost TEXT")
				database.execSQL("ALTER TABLE servers ADD COLUMN proxyPort INTEGER")
				database.execSQL("ALTER TABLE servers ADD COLUMN proxyUsername TEXT")
				database.execSQL("ALTER TABLE servers ADD COLUMN encryptedProxyPassword TEXT")
			}
		}
	}
}



