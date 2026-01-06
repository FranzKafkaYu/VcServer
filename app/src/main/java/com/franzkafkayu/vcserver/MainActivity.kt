package com.franzkafkayu.vcserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.data.AppDatabase
import com.franzkafkayu.vcserver.repositories.ServerRepositoryImpl
import com.franzkafkayu.vcserver.repositories.SettingsRepositoryImpl
import com.franzkafkayu.vcserver.services.ServerManagementServiceImpl
import com.franzkafkayu.vcserver.services.ServerMonitoringServiceImpl
import com.franzkafkayu.vcserver.services.SettingsServiceImpl
import com.franzkafkayu.vcserver.services.SshAuthenticationServiceImpl
import com.franzkafkayu.vcserver.services.SshCommandServiceImpl
import com.franzkafkayu.vcserver.services.TerminalServiceImpl
import com.franzkafkayu.vcserver.ui.navigation.NavGraph
import com.franzkafkayu.vcserver.ui.navigation.Screen
import com.franzkafkayu.vcserver.ui.screens.AddServerScreen
import com.franzkafkayu.vcserver.ui.screens.ServerListScreen
import com.franzkafkayu.vcserver.ui.theme.VcServerTheme
import android.content.Context
import com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel
import com.franzkafkayu.vcserver.ui.viewmodels.ServerListViewModel
import com.franzkafkayu.vcserver.utils.LocaleHelper
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
	override fun attachBaseContext(newBase: Context) {
		// 在 attachBaseContext 中设置语言，这样可以在 onCreate 之前应用
		val settingsRepository = SettingsRepositoryImpl(newBase)
		val locale = runBlocking {
			val settings = settingsRepository.getSettings().first()
			LocaleHelper.getLocale(settings.language)
		}
		super.attachBaseContext(LocaleHelper.wrapContext(newBase, locale))
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// 初始化依赖
		// 数据库迁移：从版本 1 到版本 2，添加 systemVersion 字段
		val migration1To2 = object : Migration(1, 2) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE servers ADD COLUMN systemVersion TEXT")
			}
		}

		// 数据库迁移：从版本 2 到版本 3，添加 orderIndex 字段
		val migration2To3 = object : Migration(2, 3) {
			override fun migrate(database: SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE servers ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
				// 为现有服务器设置默认 orderIndex（使用 id 作为初始值）
				database.execSQL("UPDATE servers SET orderIndex = id WHERE orderIndex = 0")
			}
		}

		val database = Room.databaseBuilder(
			applicationContext,
			AppDatabase::class.java,
			"vcserver_database"
		)
			.addMigrations(migration1To2, migration2To3, AppDatabase.MIGRATION_3_4)
			.build()

		val secureStorage = SecureStorage(applicationContext)
		val serverRepository = ServerRepositoryImpl(database.serverDao())
		val sshAuthService = SshAuthenticationServiceImpl(secureStorage)
		val sshCommandService = SshCommandServiceImpl()
		val serverManagementService = ServerManagementServiceImpl(
			serverRepository,
			sshAuthService,
			secureStorage
		)
		val serverMonitoringService = ServerMonitoringServiceImpl(
			sshAuthService,
			sshCommandService,
			secureStorage
		)
		val terminalService = TerminalServiceImpl()

		// 初始化设置服务
		val settingsRepository = SettingsRepositoryImpl(applicationContext)
		val settingsService = SettingsServiceImpl(settingsRepository)

		val serverListViewModel = ServerListViewModel(serverManagementService, serverMonitoringService)
		val addServerViewModel = AddServerViewModel(serverManagementService)

		setContent {
			// 获取主题设置
			val settings by settingsService.getSettings().collectAsStateWithLifecycle(initialValue = com.franzkafkayu.vcserver.models.AppSettings())

			VcServerTheme(themeMode = settings.theme) {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					val navController = rememberNavController()
					NavGraph(
						navController = navController,
						serverListViewModel = serverListViewModel,
						addServerViewModel = addServerViewModel,
						serverManagementService = serverManagementService,
						serverMonitoringService = serverMonitoringService,
						terminalService = terminalService,
						settingsService = settingsService
					)
				}
			}
		}
	}
}


