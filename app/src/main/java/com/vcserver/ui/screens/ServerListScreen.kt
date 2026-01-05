package com.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcserver.R
import com.vcserver.models.Server
import com.vcserver.ui.viewmodels.ServerListViewModel

/**
 * 服务器列表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
	viewModel: ServerListViewModel,
	onAddServerClick: () -> Unit,
	onServerClick: (Server) -> Unit,
	onConnectClick: (Server, String) -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	val snackbarHostState = remember { SnackbarHostState() }

	// 显示错误提示
	LaunchedEffect(uiState.error) {
		uiState.error?.let { error ->
			snackbarHostState.showSnackbar(
				message = error.message,
				duration = SnackbarDuration.Short
			)
			viewModel.clearError()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.server_list)) }
			)
		},
		floatingActionButton = {
			FloatingActionButton(onClick = onAddServerClick) {
				Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
			}
		},
		snackbarHost = { SnackbarHost(snackbarHostState) }
	) { paddingValues ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
		) {
			when {
				uiState.isLoading -> {
					CircularProgressIndicator(
						modifier = Modifier.align(Alignment.Center)
					)
				}
				uiState.servers.isEmpty() -> {
					EmptyServerList(
						modifier = Modifier.align(Alignment.Center),
						onAddServerClick = onAddServerClick
					)
				}
				else -> {
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						items(uiState.servers) { server ->
							ServerItem(
								server = server,
								onClick = { onServerClick(server) },
								onConnectClick = {
									viewModel.connectToServer(server) { sessionKey ->
										onConnectClick(server, sessionKey)
									}
								},
								onDeleteClick = { viewModel.deleteServer(server) },
								isConnecting = uiState.connectingServerId == server.id
							)
						}
					}
				}
			}
		}
	}
}

/**
 * 空服务器列表
 */
@Composable
fun EmptyServerList(
	modifier: Modifier = Modifier,
	onAddServerClick: () -> Unit
) {
	Column(
		modifier = modifier.padding(16.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(
			text = stringResource(R.string.empty_server_list),
			style = MaterialTheme.typography.bodyLarge
		)
		Button(onClick = onAddServerClick) {
			Text(stringResource(R.string.add_server))
		}
	}
}

/**
 * 服务器项
 */
@Composable
fun ServerItem(
	server: Server,
	onClick: () -> Unit,
	onConnectClick: () -> Unit,
	onDeleteClick: () -> Unit,
	isConnecting: Boolean = false
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Text(
					text = server.name,
					style = MaterialTheme.typography.titleMedium
				)
				Text(
					text = "${server.host}:${server.port}",
					style = MaterialTheme.typography.bodyMedium
				)
				Text(
					text = server.username,
					style = MaterialTheme.typography.bodySmall
				)
				// 显示系统版本信息（如果有）
				server.systemVersion?.let { systemVersion ->
					Text(
						text = systemVersion,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(
					onClick = onConnectClick,
					enabled = !isConnecting
				) {
					if (isConnecting) {
						CircularProgressIndicator(
							modifier = Modifier.size(24.dp),
							strokeWidth = 2.dp
						)
					} else {
						Icon(
							Icons.Default.PlayArrow,
							contentDescription = stringResource(R.string.connect)
						)
					}
				}
				IconButton(onClick = onDeleteClick) {
					Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
				}
			}
		}
	}
}

