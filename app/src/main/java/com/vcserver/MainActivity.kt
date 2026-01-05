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
import com.vcserver.data.AppDatabase
import com.vcserver.repositories.ServerRepositoryImpl
import com.vcserver.services.ServerManagementServiceImpl
import com.vcserver.services.SshAuthenticationServiceImpl
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
		val database = Room.databaseBuilder(
			applicationContext,
			AppDatabase::class.java,
			"vcserver_database"
		).build()

		val secureStorage = SecureStorage(applicationContext)
		val serverRepository = ServerRepositoryImpl(database.serverDao())
		val sshAuthService = SshAuthenticationServiceImpl(secureStorage)
		val serverManagementService = ServerManagementServiceImpl(
			serverRepository,
			sshAuthService,
			secureStorage
		)

		val serverListViewModel = ServerListViewModel(serverManagementService)
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
						addServerViewModel = addServerViewModel
					)
				}
			}
		}
	}
}



