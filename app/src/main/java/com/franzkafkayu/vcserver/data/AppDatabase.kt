package com.franzkafkayu.vcserver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.franzkafkayu.vcserver.data.dao.BandwagonLinkDao
import com.franzkafkayu.vcserver.data.dao.CloudconeLinkDao
import com.franzkafkayu.vcserver.data.dao.ServerDao
import com.franzkafkayu.vcserver.data.dao.ServerGroupDao
import com.franzkafkayu.vcserver.data.dao.VirtualizorLinkDao
import com.franzkafkayu.vcserver.data.converters.AuthTypeConverter
import com.franzkafkayu.vcserver.data.converters.ProxyTypeConverter
import com.franzkafkayu.vcserver.models.BandwagonLink
import com.franzkafkayu.vcserver.models.CloudconeLink
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.models.VirtualizorLink

/**
 * 应用数据库
 */
@Database(
	entities = [
		Server::class,
		ServerGroup::class,
		BandwagonLink::class,
		CloudconeLink::class,
		VirtualizorLink::class
	],
	version = 12,
	exportSchema = false
)
@TypeConverters(AuthTypeConverter::class, ProxyTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun serverDao(): ServerDao
	abstract fun serverGroupDao(): ServerGroupDao
	abstract fun bandwagonLinkDao(): BandwagonLinkDao
	abstract fun cloudconeLinkDao(): CloudconeLinkDao
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

		val MIGRATION_10_11 = object : Migration(10, 11) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE `virtualizor_links` ADD COLUMN `host` TEXT NOT NULL DEFAULT ''")
				database.execSQL("ALTER TABLE `virtualizor_links` ADD COLUMN `port` INTEGER NOT NULL DEFAULT 0")
				database.execSQL("ALTER TABLE `virtualizor_links` ADD COLUMN `encryptedApiKey` TEXT NOT NULL DEFAULT ''")
				database.execSQL("ALTER TABLE `virtualizor_links` ADD COLUMN `encryptedApiPass` TEXT NOT NULL DEFAULT ''")
				database.execSQL("ALTER TABLE `virtualizor_links` ADD COLUMN `certPinSha256` TEXT")
				database.execSQL(
					"""
					UPDATE `virtualizor_links` SET
						host = (SELECT host FROM virtualizor_account WHERE id = 1),
						port = (SELECT port FROM virtualizor_account WHERE id = 1),
						encryptedApiKey = (SELECT encryptedApiKey FROM virtualizor_account WHERE id = 1),
						encryptedApiPass = (SELECT encryptedApiPass FROM virtualizor_account WHERE id = 1),
						certPinSha256 = (SELECT certPinSha256 FROM virtualizor_account WHERE id = 1)
					WHERE EXISTS (SELECT 1 FROM virtualizor_account WHERE id = 1)
					""".trimIndent()
				)
				database.execSQL("DELETE FROM `virtualizor_links` WHERE host = '' OR encryptedApiKey = ''")
				database.execSQL("DROP INDEX IF EXISTS `index_virtualizor_links_vpsid`")
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_virtualizor_links_host_port_vpsid` ON `virtualizor_links` (`host`, `port`, `vpsid`)"
				)
				database.execSQL("DROP TABLE IF EXISTS `virtualizor_account`")
			}
		}

		val MIGRATION_11_12 = object : Migration(11, 12) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE `cloudcone_links` ADD COLUMN `encryptedApiKey` TEXT NOT NULL DEFAULT ''")
				database.execSQL("ALTER TABLE `cloudcone_links` ADD COLUMN `encryptedHash` TEXT NOT NULL DEFAULT ''")
				database.execSQL(
					"""
					UPDATE `cloudcone_links` SET
						encryptedApiKey = (SELECT encryptedApiKey FROM cloudcone_account WHERE id = 1),
						encryptedHash = (SELECT encryptedHash FROM cloudcone_account WHERE id = 1)
					WHERE EXISTS (SELECT 1 FROM cloudcone_account WHERE id = 1)
					""".trimIndent()
				)
				database.execSQL("DELETE FROM `cloudcone_links` WHERE encryptedApiKey = '' OR encryptedHash = ''")
				database.execSQL("DROP TABLE IF EXISTS `cloudcone_account`")
			}
		}
	}
}

internal data class VirtualizorLegacyAccount(
	val host: String,
	val port: Int,
	val encryptedApiKey: String,
	val encryptedApiPass: String,
	val certPinSha256: String?
)

internal fun migrateVirtualizorLinksFromAccount(
	account: VirtualizorLegacyAccount?,
	links: List<Pair<Long, String>>
): List<VirtualizorLink> {
	if (account == null) return emptyList()
	return links.map { (serverId, vpsid) ->
		VirtualizorLink(
			serverId = serverId,
			vpsid = vpsid,
			host = account.host,
			port = account.port,
			encryptedApiKey = account.encryptedApiKey,
			encryptedApiPass = account.encryptedApiPass,
			certPinSha256 = account.certPinSha256
		)
	}
}

internal data class CloudconeLegacyAccount(
	val encryptedApiKey: String,
	val encryptedHash: String
)

internal fun migrateCloudconeLinksFromAccount(
	account: CloudconeLegacyAccount?,
	links: List<Pair<Long, String>>
): List<CloudconeLink> {
	if (account == null) return emptyList()
	return links.map { (serverId, instanceId) ->
		CloudconeLink(
			serverId = serverId,
			instanceId = instanceId,
			encryptedApiKey = account.encryptedApiKey,
			encryptedHash = account.encryptedHash
		)
	}
}



