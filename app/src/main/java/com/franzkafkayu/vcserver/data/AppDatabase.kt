package com.franzkafkayu.vcserver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.franzkafkayu.vcserver.data.dao.ServerDao
import com.franzkafkayu.vcserver.data.dao.ServerGroupDao
import com.franzkafkayu.vcserver.data.converters.AuthTypeConverter
import com.franzkafkayu.vcserver.data.converters.ProxyTypeConverter
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup

/**
 * 应用数据库
 */
@Database(
	entities = [Server::class, ServerGroup::class],
	version = 5,
	exportSchema = false
)
@TypeConverters(AuthTypeConverter::class, ProxyTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun serverDao(): ServerDao
	abstract fun serverGroupDao(): ServerGroupDao

	companion object {
		/**
		 * 数据库迁移：从版本3迁移到版本4
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

		/**
		 * 数据库迁移：从版本4迁移到版本5
		 * 添加服务器分组功能
		 */
		val MIGRATION_4_5 = object : Migration(4, 5) {
			override fun migrate(database: SupportSQLiteDatabase) {
				// 创建服务器分组表
				database.execSQL("""
					CREATE TABLE IF NOT EXISTS server_groups (
						id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						name TEXT NOT NULL,
						createdAt INTEGER NOT NULL,
						updatedAt INTEGER NOT NULL,
						UNIQUE(name)
					)
				""".trimIndent())

				// 创建唯一索引
				database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_server_groups_name ON server_groups(name)")

				// 为服务器表添加 groupId 字段
				database.execSQL("ALTER TABLE servers ADD COLUMN groupId INTEGER")
			}
		}
	}
}



