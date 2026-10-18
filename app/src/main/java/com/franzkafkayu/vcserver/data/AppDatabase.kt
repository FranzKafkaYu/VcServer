package com.franzkafkayu.vcserver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.franzkafkayu.vcserver.data.dao.BandwagonLinkDao
import com.franzkafkayu.vcserver.data.dao.CloudconeAccountDao
import com.franzkafkayu.vcserver.data.dao.CloudconeLinkDao
import com.franzkafkayu.vcserver.data.dao.ServerDao
import com.franzkafkayu.vcserver.data.dao.ServerGroupDao
import com.franzkafkayu.vcserver.data.dao.VirtualizorAccountDao
import com.franzkafkayu.vcserver.data.dao.VirtualizorLinkDao
import com.franzkafkayu.vcserver.data.converters.AuthTypeConverter
import com.franzkafkayu.vcserver.data.converters.ProxyTypeConverter
import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeAccount
import com.franzkafkayu.vcserver.models.CloudconeLink
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.models.VirtualizorAccount
import com.franzkafkayu.vcserver.models.VirtualizorLink

/**
 * 应用数据库
 */
@Database(
	entities = [
		Server::class,
		ServerGroup::class,
		BandwagonLink::class,
		CloudconeAccount::class,
		CloudconeLink::class,
		VirtualizorAccount::class,
		VirtualizorLink::class
	],
	version = 10,
	exportSchema = false
)
@TypeConverters(AuthTypeConverter::class, ProxyTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun serverDao(): ServerDao
	abstract fun serverGroupDao(): ServerGroupDao
	abstract fun bandwagonLinkDao(): BandwagonLinkDao
	abstract fun cloudconeAccountDao(): CloudconeAccountDao
	abstract fun cloudconeLinkDao(): CloudconeLinkDao
	abstract fun virtualizorAccountDao(): VirtualizorAccountDao
	abstract fun virtualizorLinkDao(): VirtualizorLinkDao

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

		/**
		 * 数据库迁移：从版本5迁移到版本6
		 * 添加服务器置顶
		 */
		val MIGRATION_5_6 = object : Migration(5, 6) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE servers ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
			}
		}

		/**
		 * 数据库迁移：从版本6迁移到版本7
		 * 添加可空的跳板引用
		 */
		val MIGRATION_6_7 = object : Migration(6, 7) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE servers ADD COLUMN jumpHostId INTEGER")
			}
		}

		/**
		 * 数据库迁移：从版本7迁移到版本8
		 * 搬瓦工绑定单独成表，不改 servers 列
		 */
		val MIGRATION_7_8 = object : Migration(7, 8) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS `bandwagon_links` (
						`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						`serverId` INTEGER NOT NULL,
						`veid` TEXT NOT NULL,
						`encryptedApiKey` TEXT NOT NULL
					)
					""".trimIndent()
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_bandwagon_links_serverId` ON `bandwagon_links` (`serverId`)"
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_bandwagon_links_veid` ON `bandwagon_links` (`veid`)"
				)
			}
		}

		/**
		 * 数据库迁移：从版本8迁移到版本9
		 * CloudCone 账号与实例绑定，不改 servers 和 bandwagon_links
		 */
		val MIGRATION_8_9 = object : Migration(8, 9) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS `cloudcone_account` (
						`id` INTEGER NOT NULL,
						`encryptedApiKey` TEXT NOT NULL,
						`encryptedHash` TEXT NOT NULL,
						PRIMARY KEY(`id`)
					)
					""".trimIndent()
				)
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS `cloudcone_links` (
						`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						`serverId` INTEGER NOT NULL,
						`instanceId` TEXT NOT NULL
					)
					""".trimIndent()
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_cloudcone_links_serverId` ON `cloudcone_links` (`serverId`)"
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_cloudcone_links_instanceId` ON `cloudcone_links` (`instanceId`)"
				)
			}
		}

		val MIGRATION_9_10 = object : Migration(9, 10) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS `virtualizor_account` (
						`id` INTEGER NOT NULL,
						`host` TEXT NOT NULL,
						`port` INTEGER NOT NULL,
						`encryptedApiKey` TEXT NOT NULL,
						`encryptedApiPass` TEXT NOT NULL,
						`certPinSha256` TEXT,
						PRIMARY KEY(`id`)
					)
					""".trimIndent()
				)
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS `virtualizor_links` (
						`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						`serverId` INTEGER NOT NULL,
						`vpsid` TEXT NOT NULL
					)
					""".trimIndent()
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_virtualizor_links_serverId` ON `virtualizor_links` (`serverId`)"
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_virtualizor_links_vpsid` ON `virtualizor_links` (`vpsid`)"
				)
			}
		}
	}
}



