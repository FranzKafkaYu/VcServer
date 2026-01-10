package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.ui.viewmodels.ServerListViewModel
import com.franzkafkayu.vcserver.ui.screens.GroupHeader

/**
 * 服务器列表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
	viewModel: ServerListViewModel,
	groupManagementViewModel: com.franzkafkayu.vcserver.ui.viewmodels.ServerGroupManagementViewModel? = null,
	onAddServerClick: () -> Unit,
	onEditServerClick: (Long) -> Unit,
	onConnectClick: (Server, String) -> Unit,
	onSettingsClick: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	val snackbarHostState = remember { SnackbarHostState() }
	var showGroupManagementDialog by remember { mutableStateOf(false) }

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
				title = { 
					Text(
						if (uiState.isSelectionMode) {
							stringResource(R.string.select_mode) + " (${uiState.selectedServerIds.size})"
						} else {
							stringResource(R.string.server_list)
						}
					) 
				},
				actions = {
					if (uiState.isSelectionMode) {
						IconButton(onClick = { viewModel.toggleSelectAll() }) {
							Icon(
								if (uiState.selectedServerIds.size == uiState.servers.size) {
									Icons.Default.CheckBox
								} else {
									Icons.Default.CheckBoxOutlineBlank
								},
								contentDescription = stringResource(R.string.select_all)
							)
						}
						IconButton(onClick = { viewModel.exitSelectionMode() }) {
							Icon(
								Icons.Default.Done,
								contentDescription = stringResource(R.string.done)
							)
						}
					} else {
						if (groupManagementViewModel != null) {
							IconButton(onClick = { showGroupManagementDialog = true }) {
								Icon(
									Icons.Default.Folder,
									contentDescription = stringResource(R.string.manage_groups)
								)
							}
						}
						IconButton(onClick = onSettingsClick) {
							Icon(
								Icons.Default.Settings,
								contentDescription = stringResource(R.string.settings)
							)
						}
					}
				}
			)
		},
		floatingActionButton = {
			if (!uiState.isSelectionMode) {
				FloatingActionButton(onClick = onAddServerClick) {
					Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
				}
			}
		},
		bottomBar = {
			if (uiState.isSelectionMode && uiState.selectedServerIds.isNotEmpty()) {
				BottomAppBar {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = stringResource(R.string.delete_items, uiState.selectedServerIds.size),
							modifier = Modifier.weight(1f)
						)
						Button(
							onClick = { viewModel.showBatchDeleteConfirmDialog() }
						) {
							Text(stringResource(R.string.delete))
						}
					}
				}
			}
		},
		snackbarHost = { SnackbarHost(snackbarHostState) }
	) { paddingValues ->
		// 单个删除确认对话框
		uiState.serverToDelete?.let { server ->
			AlertDialog(
				onDismissRequest = { viewModel.cancelDelete() },
				title = { Text(stringResource(R.string.delete_server_confirm)) },
				text = { 
					Text(stringResource(R.string.delete_server_confirm_message, server.name, server.host, server.port))
				},
				confirmButton = {
					TextButton(
						onClick = { viewModel.confirmDeleteServer() }
					) {
						Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
					}
				},
				dismissButton = {
					TextButton(
						onClick = { viewModel.cancelDelete() }
					) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}

		// 批量删除确认对话框
		if (uiState.showBatchDeleteConfirm) {
			AlertDialog(
				onDismissRequest = { viewModel.cancelBatchDelete() },
				title = { Text(stringResource(R.string.delete)) },
				text = { 
					Text(
						stringResource(
							R.string.delete_confirm,
							uiState.selectedServerIds.size
						)
					)
				},
				confirmButton = {
					TextButton(
						onClick = { viewModel.confirmDeleteSelectedServers() }
					) {
						Text(stringResource(R.string.confirm))
					}
				},
				dismissButton = {
					TextButton(
						onClick = { viewModel.cancelBatchDelete() }
					) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}

		// 分组管理对话框
		if (showGroupManagementDialog && groupManagementViewModel != null) {
			ServerGroupManagementDialog(
				viewModel = groupManagementViewModel,
				onDismiss = { showGroupManagementDialog = false }
			)
		}

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
						// 显示分组和分组下的服务器
						uiState.groupedServers.forEach { (group, servers) ->
							val isExpanded = uiState.expandedGroupIds.contains(group.id)
							
							// 分组标题
							item(key = "group_${group.id}") {
								GroupHeader(
									groupName = group.name,
									serverCount = servers.size,
									isExpanded = isExpanded,
									onClick = { viewModel.toggleGroupExpanded(group.id) }
								)
							}
							
							// 分组下的服务器（可折叠）
							if (isExpanded) {
								items(
									items = servers,
									key = { it.id }
								) { server ->
									ServerItem(
										server = server,
										isSelected = uiState.selectedServerIds.contains(server.id),
										isSelectionMode = uiState.isSelectionMode,
										onClick = {
											if (uiState.isSelectionMode) {
												viewModel.toggleServerSelection(server.id)
											} else {
												viewModel.connectToServer(server) { sessionKey ->
													onConnectClick(server, sessionKey)
												}
											}
										},
										onLongClick = {
											if (!uiState.isSelectionMode) {
												viewModel.enterSelectionMode(server.id)
											}
										},
										onEditClick = {
											if (!uiState.isSelectionMode) {
												onEditServerClick(server.id)
											}
										},
										onConnectClick = {
											if (!uiState.isSelectionMode) {
												viewModel.connectToServer(server) { sessionKey ->
													onConnectClick(server, sessionKey)
												}
											}
										},
										onDeleteClick = { viewModel.showDeleteConfirmDialog(server) },
										isConnecting = uiState.connectingServerId == server.id
									)
								}
							}
						}
						
						// 未分组的服务器
						if (uiState.ungroupedServers.isNotEmpty()) {
							val isUngroupedExpanded = uiState.expandedGroupIds.contains(-1L)
							item(key = "ungrouped_header") {
								GroupHeader(
									groupName = stringResource(R.string.ungrouped_servers),
									serverCount = uiState.ungroupedServers.size,
									isExpanded = isUngroupedExpanded,
									onClick = { viewModel.toggleGroupExpanded(-1L) }
								)
							}
							
							if (isUngroupedExpanded) {
								items(
									items = uiState.ungroupedServers,
									key = { it.id }
								) { server ->
									ServerItem(
										server = server,
										isSelected = uiState.selectedServerIds.contains(server.id),
										isSelectionMode = uiState.isSelectionMode,
										onClick = {
											if (uiState.isSelectionMode) {
												viewModel.toggleServerSelection(server.id)
											} else {
												viewModel.connectToServer(server) { sessionKey ->
													onConnectClick(server, sessionKey)
												}
											}
										},
										onLongClick = {
											if (!uiState.isSelectionMode) {
												viewModel.enterSelectionMode(server.id)
											}
										},
										onEditClick = {
											if (!uiState.isSelectionMode) {
												onEditServerClick(server.id)
											}
										},
										onConnectClick = {
											if (!uiState.isSelectionMode) {
												viewModel.connectToServer(server) { sessionKey ->
													onConnectClick(server, sessionKey)
												}
											}
										},
										onDeleteClick = { viewModel.showDeleteConfirmDialog(server) },
										isConnecting = uiState.connectingServerId == server.id
									)
								}
							}
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerItem(
	server: Server,
	isSelected: Boolean = false,
	isSelectionMode: Boolean = false,
	onClick: () -> Unit,
	onLongClick: () -> Unit = {},
	onEditClick: () -> Unit,
	onConnectClick: () -> Unit,
	onDeleteClick: () -> Unit,
	isConnecting: Boolean = false
) {
	if (isSelectionMode) {
		// 选择模式下，显示复选框，不使用 SwipeToDismiss
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
				Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.weight(1f)
				) {
					Icon(
						if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
						contentDescription = null,
						tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
					)
					Column(
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
						server.systemVersion?.let { systemVersion ->
							Text(
								text = systemVersion,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.primary
							)
						}
					}
				}
			}
		}
	} else {
		// 正常模式下，显示卡片（左滑删除功能暂时简化，后续可以添加）
		Card(
			modifier = Modifier
				.fillMaxWidth()
				.clickable(onClick = onClick, enabled = !isConnecting)
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
								modifier = Modifier.size(28.dp),
								color = MaterialTheme.colorScheme.primary,
								strokeWidth = 4.dp,
								trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
							)
						} else {
							Icon(
								Icons.Default.PlayArrow,
								contentDescription = stringResource(R.string.connect)
							)
						}
					}
					IconButton(
						onClick = onEditClick
					) {
						Icon(
							Icons.Default.Edit,
							contentDescription = stringResource(R.string.edit)
						)
					}
					IconButton(
						onClick = onDeleteClick
					) {
						Icon(
							Icons.Default.Delete,
							contentDescription = stringResource(R.string.delete)
						)
					}
				}
			}
		}
	}
}
