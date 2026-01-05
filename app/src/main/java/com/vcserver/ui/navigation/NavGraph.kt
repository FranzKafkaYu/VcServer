package com.vcserver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jcraft.jsch.Session
import com.vcserver.services.TerminalService
import com.vcserver.ui.screens.AddServerScreen
import com.vcserver.ui.screens.ServerListScreen
import com.vcserver.ui.screens.ServerMonitoringScreen
import com.vcserver.ui.screens.TerminalScreen
import com.vcserver.ui.viewmodels.ServerMonitoringViewModel
import com.vcserver.ui.viewmodels.TerminalViewModel
import com.vcserver.utils.SessionManager

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
	object ServerList : Screen("server_list")
	object AddServer : Screen("add_server")
	data class ServerMonitoring(val serverId: Long, val sessionKey: String) : Screen("server_monitoring/{serverId}/{sessionKey}") {
		companion object {
			fun createRoute(serverId: Long, sessionKey: String) = "server_monitoring/$serverId/$sessionKey"
		}
	}
	data class Terminal(val serverId: Long, val sessionKey: String) : Screen("terminal/{serverId}/{sessionKey}") {
		companion object {
			fun createRoute(serverId: Long, sessionKey: String) = "terminal/$serverId/$sessionKey"
		}
	}
}

/**
 * 导航图
 */
@Composable
fun NavGraph(
	navController: NavHostController,
	serverListViewModel: com.vcserver.ui.viewmodels.ServerListViewModel,
	addServerViewModel: com.vcserver.ui.viewmodels.AddServerViewModel,
	serverMonitoringService: com.vcserver.services.ServerMonitoringService,
	terminalService: TerminalService
) {
	NavHost(
		navController = navController,
		startDestination = Screen.ServerList.route
	) {
		composable(Screen.ServerList.route) {
			ServerListScreen(
				viewModel = serverListViewModel,
				onAddServerClick = {
					navController.navigate(Screen.AddServer.route)
				},
				onServerClick = { server ->
					// TODO: 后续实现服务器详情功能
				},
				onConnectClick = { server, sessionKey ->
					navController.navigate(Screen.ServerMonitoring.createRoute(server.id, sessionKey))
				}
			)
		}
		composable(Screen.AddServer.route) {
			AddServerScreen(
				viewModel = addServerViewModel,
				onBackClick = {
					navController.popBackStack()
				},
				onSaveSuccess = {
					navController.popBackStack()
				}
			)
		}
		composable(
			route = "server_monitoring/{serverId}/{sessionKey}",
			arguments = listOf(
				navArgument("serverId") { type = NavType.LongType },
				navArgument("sessionKey") { type = NavType.StringType }
			)
		) { backStackEntry ->
			val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
			val sessionKey = backStackEntry.arguments?.getString("sessionKey") ?: ""
			val session = SessionManager.getSession(sessionKey)
			
			if (session != null) {
				// 获取服务器信息
				val server = serverListViewModel.uiState.value.servers.find { it.id == serverId }
				if (server != null) {
					val viewModel = viewModel<ServerMonitoringViewModel>(
						factory = ServerMonitoringViewModelFactory(
							serverMonitoringService = serverMonitoringService,
							server = server,
							session = session
						)
					)
					ServerMonitoringScreen(
						viewModel = viewModel,
						onBackClick = {
							// 从监控界面返回时才断开 SSH session
							// 从终端返回时不应该断开（终端只断开 Shell channel）
							SessionManager.removeSession(sessionKey)
							navController.popBackStack()
						},
						onEnterTerminal = {
							navController.navigate(Screen.Terminal.createRoute(serverId, sessionKey))
						}
					)
				}
			}
		}
		composable(
			route = "terminal/{serverId}/{sessionKey}",
			arguments = listOf(
				navArgument("serverId") { type = NavType.LongType },
				navArgument("sessionKey") { type = NavType.StringType }
			)
		) { backStackEntry ->
			val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
			val sessionKey = backStackEntry.arguments?.getString("sessionKey") ?: ""
			val session = SessionManager.getSession(sessionKey)
			
			if (session != null) {
				// 获取服务器信息
				val server = serverListViewModel.uiState.value.servers.find { it.id == serverId }
				if (server != null) {
					val viewModel = viewModel<TerminalViewModel>(
						factory = TerminalViewModelFactory(
							terminalService = terminalService,
							serverMonitoringService = serverMonitoringService,
							server = server,
							session = session,
							sessionKey = sessionKey
						)
					)
					TerminalScreen(
						viewModel = viewModel,
						onBackClick = {
							navController.popBackStack()
						}
					)
				}
			}
		}
	}
}

/**
 * ServerMonitoringViewModel 工厂
 */
class ServerMonitoringViewModelFactory(
	private val serverMonitoringService: com.vcserver.services.ServerMonitoringService,
	private val server: com.vcserver.models.Server,
	private val session: Session
) : androidx.lifecycle.ViewModelProvider.Factory {
	override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(ServerMonitoringViewModel::class.java)) {
			@Suppress("UNCHECKED_CAST")
			return ServerMonitoringViewModel(serverMonitoringService, server, session) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class")
	}
}

/**
 * TerminalViewModel 工厂
 */
class TerminalViewModelFactory(
	private val terminalService: TerminalService,
	private val serverMonitoringService: com.vcserver.services.ServerMonitoringService,
	private val server: com.vcserver.models.Server,
	private val session: Session,
	private val sessionKey: String
) : androidx.lifecycle.ViewModelProvider.Factory {
	override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(TerminalViewModel::class.java)) {
			@Suppress("UNCHECKED_CAST")
			return TerminalViewModel(terminalService, serverMonitoringService, server, session, sessionKey) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class")
	}
}



