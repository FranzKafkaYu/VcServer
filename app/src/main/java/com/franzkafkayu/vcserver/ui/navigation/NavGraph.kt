package com.franzkafkayu.vcserver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.franzkafkayu.vcserver.ssh.SshClientSession
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
    object PrivacyPolicy : Screen("privacy_policy")
    data class Traceroute(val serverId: Long) : Screen("traceroute/{serverId}") {
        companion object {
            fun createRoute(serverId: Long) = "traceroute/$serverId"
        }
    }
}

/**
 * 导航�?
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    serverListViewModel: com.franzkafkayu.vcserver.ui.viewmodels.ServerListViewModel,
    addServerViewModel: com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel,
    serverGroupManagementViewModel: com.franzkafkayu.vcserver.ui.viewmodels.ServerGroupManagementViewModel? = null,
    serverGroupRepository: com.franzkafkayu.vcserver.repositories.ServerGroupRepository? = null,
    serverManagementService: com.franzkafkayu.vcserver.services.ServerManagementService,
    serverMonitoringService: com.franzkafkayu.vcserver.services.ServerMonitoringService,
    terminalService: TerminalService,
    terminalPreferences: com.franzkafkayu.vcserver.utils.TerminalPreferences,
    settingsService: com.franzkafkayu.vcserver.services.SettingsService,
    exportImportService: com.franzkafkayu.vcserver.services.DatabaseExportImportService,
    sftpFileTransferService: com.franzkafkayu.vcserver.services.SftpFileTransferService
) {
    NavHost(
        navController = navController,
        startDestination = com.franzkafkayu.vcserver.ui.navigation.Screen.ServerList.route
    ) {
        composable(com.franzkafkayu.vcserver.ui.navigation.Screen.ServerList.route) {
            com.franzkafkayu.vcserver.ui.screens.ServerListScreen(
                viewModel = serverListViewModel,
                groupManagementViewModel = serverGroupManagementViewModel,
                onAddServerClick = {
                    navController.navigate(com.franzkafkayu.vcserver.ui.navigation.Screen.AddServer.route)
                },
                onEditServerClick = { serverId ->
                    navController.navigate(
                        com.franzkafkayu.vcserver.ui.navigation.Screen.EditServer.createRoute(
                            serverId
                        )
                    )
                },
                onConnectClick = { server, sessionKey ->
                    navController.navigate(
                        com.franzkafkayu.vcserver.ui.navigation.Screen.ServerMonitoring.createRoute(
                            server.id,
                            sessionKey
                        )
                    )
                },
                onSettingsClick = {
                    navController.navigate(com.franzkafkayu.vcserver.ui.navigation.Screen.Settings.route)
                },
                onTraceClick = { server ->
                    navController.navigate(
                        com.franzkafkayu.vcserver.ui.navigation.Screen.Traceroute.createRoute(server.id)
                    )
                }
            )
        }
        composable(com.franzkafkayu.vcserver.ui.navigation.Screen.AddServer.route) {
            com.franzkafkayu.vcserver.ui.screens.AddServerScreen(
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
                    factory = com.franzkafkayu.vcserver.ui.navigation.EditServerViewModelFactory(
                        serverManagementService = serverManagementService,
                        settingsService = settingsService,
                        serverGroupRepository = serverGroupRepository,
                        serverId = serverId
                    )
                )
            com.franzkafkayu.vcserver.ui.screens.AddServerScreen(
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
                    val viewModel = viewModel<com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringViewModel>(
                        factory = com.franzkafkayu.vcserver.ui.navigation.ServerMonitoringViewModelFactory(
                            serverMonitoringService = serverMonitoringService,
                            sftpFileTransferService = sftpFileTransferService,
                            server = server,
                            session = session
                        )
                    )
                    com.franzkafkayu.vcserver.ui.screens.ServerMonitoringScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            // 只从导航态移除 Session 引用，不断开连接池
                            SessionManager.removeSession(sessionKey)
                            navController.popBackStack()
                        },
                        onEnterTerminal = {
                            navController.navigate(
                                com.franzkafkayu.vcserver.ui.navigation.Screen.Terminal.createRoute(
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
                    val viewModel = viewModel<com.franzkafkayu.vcserver.ui.viewmodels.TerminalViewModel>(
                        factory = com.franzkafkayu.vcserver.ui.navigation.TerminalViewModelFactory(
                            terminalService = terminalService,
                            serverMonitoringService = serverMonitoringService,
                            server = server,
                            session = session,
                            sessionKey = sessionKey,
                            terminalPreferences = terminalPreferences
                        )
                    )
                    com.franzkafkayu.vcserver.ui.screens.TerminalScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
        composable(
            route = "traceroute/{serverId}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
            val server = serverListViewModel.uiState.value.servers.find { it.id == serverId }
            if (server != null) {
                val tracerouteViewModel = viewModel<com.franzkafkayu.vcserver.ui.viewmodels.TracerouteViewModel>(
                    factory = TracerouteViewModelFactory(server.name, server.host, server.port)
                )
                com.franzkafkayu.vcserver.ui.screens.TracerouteScreen(
                    viewModel = tracerouteViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
        composable(com.franzkafkayu.vcserver.ui.navigation.Screen.Settings.route) {
            val settingsViewModel = viewModel<com.franzkafkayu.vcserver.ui.viewmodels.SettingsViewModel>(
                factory = com.franzkafkayu.vcserver.ui.navigation.SettingsViewModelFactory(
                    settingsService,
                    exportImportService
                )
            )
            com.franzkafkayu.vcserver.ui.screens.SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onAboutClick = {
                    navController.navigate(com.franzkafkayu.vcserver.ui.navigation.Screen.About.route)
                }
            )
        }
        composable(com.franzkafkayu.vcserver.ui.navigation.Screen.About.route) {
            com.franzkafkayu.vcserver.ui.screens.AboutScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onPrivacyPolicyClick = {
                    navController.navigate(com.franzkafkayu.vcserver.ui.navigation.Screen.PrivacyPolicy.route)
                }
            )
        }
        composable(com.franzkafkayu.vcserver.ui.navigation.Screen.PrivacyPolicy.route) {
            com.franzkafkayu.vcserver.ui.screens.PrivacyPolicyScreen(
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
    private val sftpFileTransferService: com.franzkafkayu.vcserver.services.SftpFileTransferService,
    private val server: com.franzkafkayu.vcserver.models.Server,
    private val session: SshClientSession
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringViewModel(
                serverMonitoringService,
                sftpFileTransferService,
                server,
                session
            ) as T
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
    private val session: SshClientSession,
    private val sessionKey: String,
    private val terminalPreferences: com.franzkafkayu.vcserver.utils.TerminalPreferences
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.TerminalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.TerminalViewModel(
                terminalService,
                serverMonitoringService,
                server,
                session,
                sessionKey,
                terminalPreferences
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
    private val serverGroupRepository: com.franzkafkayu.vcserver.repositories.ServerGroupRepository?,
    private val serverId: Long
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel(
                serverManagementService,
                settingsService,
                serverGroupRepository,
                serverId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * SettingsViewModel 工厂
 */
class TracerouteViewModelFactory(
    private val serverName: String,
    private val host: String,
    private val port: Int
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.TracerouteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.TracerouteViewModel(serverName, host, port) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val settingsService: com.franzkafkayu.vcserver.services.SettingsService,
    private val exportImportService: com.franzkafkayu.vcserver.services.DatabaseExportImportService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.franzkafkayu.vcserver.ui.viewmodels.SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.franzkafkayu.vcserver.ui.viewmodels.SettingsViewModel(
                settingsService,
                exportImportService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



