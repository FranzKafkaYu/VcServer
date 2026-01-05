package com.vcserver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vcserver.ui.screens.AddServerScreen
import com.vcserver.ui.screens.ServerListScreen

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
	object ServerList : Screen("server_list")
	object AddServer : Screen("add_server")
}

/**
 * 导航图
 */
@Composable
fun NavGraph(
	navController: NavHostController,
	serverListViewModel: com.vcserver.ui.viewmodels.ServerListViewModel,
	addServerViewModel: com.vcserver.ui.viewmodels.AddServerViewModel
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
					// TODO: 后续实现服务器连接功能
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
	}
}



