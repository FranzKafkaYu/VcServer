package com.vcserver

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
import com.vcserver.data.AppDatabase
import com.vcserver.repositories.ServerRepositoryImpl
import com.vcserver.services.ServerManagementServiceImpl
import com.vcserver.services.ServerMonitoringServiceImpl
import com.vcserver.services.SshAuthenticationServiceImpl
import com.vcserver.services.SshCommandServiceImpl
import com.vcserver.services.TerminalServiceImpl
import com.vcserver.ui.navigation.NavGraph
import com.vcserver.ui.navigation.Screen
import com.vcserver.ui.screens.AddServerScreen
import com.vcserver.ui.screens.ServerListScreen
import com.vcserver.ui.theme.VcServerTheme
import com.vcserver.ui.viewmodels.AddServerViewModel
import com.vcserver.ui.viewmodels.ServerListViewModel
import com.vcserver.utils.SecureStorage

class MainActivity : ComponentActivity() {
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
			.addMigrations(migration1To2, migration2To3)
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

		val serverListViewModel = ServerListViewModel(serverManagementService, serverMonitoringService)
		val addServerViewModel = AddServerViewModel(serverManagementService)

		setContent {
			VcServerTheme {
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
						terminalService = terminalService
					)
				}
			}
		}
	}
}



