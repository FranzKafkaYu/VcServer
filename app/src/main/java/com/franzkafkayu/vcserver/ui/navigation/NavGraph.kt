package com.franzkafkayu.vcserver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.services.TerminalService
import com.franzkafkayu.vcserver.ui.screens.AboutScreen
import com.franzkafkayu.vcserver.ui.screens.AddServerScreen
import com.franzkafkayu.vcserver.ui.screens.ServerListScreen
import com.franzkafkayu.vcserver.ui.screens.ServerMonitoringScreen
import com.franzkafkayu.vcserver.ui.screens.SettingsScreen
import com.franzkafkayu.vcserver.ui.screens.TerminalScreen
import com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringViewModel
import com.franzkafkayu.vcserver.ui.viewmodels.SettingsViewModel
import com.franzkafkayu.vcserver.ui.viewmodels.TerminalViewModel
import com.franzkafkayu.vcserver.utils.SessionManager

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
    object ServerList : Screen("server_list")
    object AddServer : Screen("add_server")
    data class EditServer(val serverId: Long) : Screen("edit_server/{serverId}") {
        companion object {
            fun createRoute(serverId: Long) = "edit_server/$serverId"
        }
    }

    data class ServerMonitoring(val serverId: Long, val sessionKey: String) :
        Screen("server_monitoring/{serverId}/{sessionKey}") {
        companion object {
            fun createRoute(serverId: Long, sessionKey: String) =
                "server_monitoring/$serverId/$sessionKey"
        }
    }

    data class Terminal(val serverId: Long, val sessionKey: String) :
        Screen("terminal/{serverId}/{sessionKey}") {
        companion object {
            fun createRoute(serverId: Long, sessionKey: String) = "terminal/$serverId/$sessionKey"
        }
    }

    object Settings : Screen("settings")
    object About : Screen("about")
}

/**
 * 导航�?
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    serverListViewModel: com.franzkafkayu.vcserver.ui.viewmodels.ServerListViewModel,
    addServerViewModel: com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel,
    serverManagementService: com.franzkafkayu.vcserver.services.ServerManagementService,
    serverMonitoringService: com.franzkafkayu.vcserver.services.ServerMonitoringService,
    terminalService: TerminalService,
    settingsService: com.franzkafkayu.vcserver.services.SettingsService,
    exportImportService: com.franzkafkayu.vcserver.services.DatabaseExportImportService
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
                onEditServerClick = { serverId ->
                    navController.navigate(Screen.EditServer.createRoute(serverId))
                },
                onConnectClick = { server, sessionKey ->
                    navController.navigate(
                        Screen.ServerMonitoring.createRoute(
                            server.id,
                            sessionKey
                        )
                    )
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
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
            route = "edit_server/{serverId}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
            val editServerViewModel =
                viewModel<com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel>(
                    factory = EditServerViewModelFactory(
                        serverManagementService = serverManagementService,
                        settingsService = settingsService,
                        serverId = serverId
                    )
                )
            AddServerScreen(
                viewModel = editServerViewModel,
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
                // 获取服务器信�?
                val server =
                    serverListViewModel.uiState.value.servers.find { server -> server.id == serverId }
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
                            // 从终端返回时不应该断开（终端只断开 Shell channel�?
                            SessionManager.removeSession(sessionKey)
                            navController.popBackStack()
                        },
                        onEnterTerminal = {
                            navController.navigate(
                                Screen.Terminal.createRoute(
                                    serverId,
                                    sessionKey
                                )
                            )
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
                // 获取服务器信�?
                val server =
                    serverListViewModel.uiState.value.servers.find { server -> server.id == serverId }
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
        composable(Screen.Settings.route) {
            val settingsViewModel = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(settingsService, exportImportService)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onAboutClick = {
                    navController.navigate(Screen.About.route)
                }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * ServerMonitoringViewModel 工厂
 */
class ServerMonitoringViewModelFactory(
    private val serverMonitoringService: com.franzkafkayu.vcserver.services.ServerMonitoringService,
    private val server: com.franzkafkayu.vcserver.models.Server,
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
    private val serverMonitoringService: com.franzkafkayu.vcserver.services.ServerMonitoringService,
    private val server: com.franzkafkayu.vcserver.models.Server,
    private val session: Session,
    private val sessionKey: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TerminalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TerminalViewModel(
                terminalService,
                serverMonitoringService,
                server,
                session,
                sessionKey
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * EditServerViewModel 工厂
 */
class EditServerViewModelFactory(
    private val serverManagementService: com.franzkafkayu.vcserver.services.ServerManagementService,
    private val settingsService: com.franzkafkayu.vcserver.services.SettingsService,
    private val serverId: Long
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel(
                serverManagementService,
                settingsService,
                serverId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * SettingsViewModel 工厂
 */
class SettingsViewModelFactory(
    private val settingsService: com.franzkafkayu.vcserver.services.SettingsService,
    private val exportImportService: com.franzkafkayu.vcserver.services.DatabaseExportImportService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsService, exportImportService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



