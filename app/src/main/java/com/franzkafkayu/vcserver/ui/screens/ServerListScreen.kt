package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.services.VendorKind
import com.franzkafkayu.vcserver.services.VendorMenu
import com.franzkafkayu.vcserver.services.VendorMenuEntry
import com.franzkafkayu.vcserver.services.vendorMenu
import com.franzkafkayu.vcserver.ui.viewmodels.ServerListViewModel
import com.franzkafkayu.vcserver.ui.viewmodels.VendorPrompt
import com.franzkafkayu.vcserver.utils.AppError
import androidx.compose.ui.text.input.PasswordVisualTransformation

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

	val errorMessage = uiState.error?.let { listErrorMessage(it) }
	LaunchedEffect(errorMessage) {
		errorMessage?.let { message ->
			snackbarHostState.showSnackbar(
				message = message,
				duration = SnackbarDuration.Short
			)
			viewModel.clearError()
		}
	}

	val noticeText = uiState.noticeMessageRes?.let { res ->
		val arg = uiState.noticeMessageArg
		if (arg != null) stringResource(res, arg) else stringResource(res)
	}
	LaunchedEffect(noticeText) {
		noticeText?.let { message ->
			snackbarHostState.showSnackbar(
				message = message,
				duration = SnackbarDuration.Short
			)
			viewModel.clearNotice()
		}
	}

	var searchText by remember { mutableStateOf(uiState.searchQuery) }
	
	// 同步搜索文本与状态
	LaunchedEffect(uiState.searchQuery) {
		searchText = uiState.searchQuery
	}
	
	Scaffold(
		topBar = {
			Column {
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
							IconButton(onClick = { viewModel.toggleSearch() }) {
								Icon(
									Icons.Default.Search,
									contentDescription = stringResource(R.string.search)
								)
							}
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
				
				// 搜索栏
				if (uiState.isSearchActive) {
					Card(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 8.dp, vertical = 4.dp),
						colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.surface
						)
					) {
						Column {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								OutlinedTextField(
									value = searchText,
									onValueChange = { 
										searchText = it
										viewModel.updateSearchQuery(it)
									},
									modifier = Modifier.weight(1f),
									placeholder = { Text(stringResource(R.string.search_hint)) },
									singleLine = true,
									leadingIcon = {
										Icon(
											Icons.Default.Search,
											contentDescription = null
										)
									},
									trailingIcon = {
										if (searchText.isNotEmpty()) {
											IconButton(onClick = { 
												viewModel.clearSearchQuery()
												searchText = ""
											}) {
												Icon(
													Icons.Default.Close,
													contentDescription = stringResource(R.string.clear_search)
												)
											}
										}
									},
									colors = OutlinedTextFieldDefaults.colors(
										focusedBorderColor = MaterialTheme.colorScheme.primary
									)
								)
							}
							// 显示语法错误
							uiState.searchSyntaxError?.let { error ->
								Text(
									text = stringResource(R.string.search_syntax_error, error),
									color = MaterialTheme.colorScheme.error,
									style = MaterialTheme.typography.bodySmall,
									modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
								)
							}
							// 显示匹配数量
							if (uiState.searchQuery.isNotEmpty()) {
								val totalCount = uiState.groupedServers.sumOf { it.second.size } + uiState.ungroupedServers.size
								Text(
									text = stringResource(R.string.search_results, totalCount),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
								)
							}
						}
					}
				}
			}
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

		VendorPromptDialog(uiState.vendorPrompt, viewModel)

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
				uiState.searchQuery.isNotEmpty() && 
					uiState.groupedServers.isEmpty() && 
					uiState.ungroupedServers.isEmpty() -> {
					// 搜索无结果
					Column(
						modifier = Modifier.align(Alignment.Center),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Text(
							text = stringResource(R.string.search_no_results),
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						TextButton(onClick = { viewModel.clearSearchQuery() }) {
							Text(stringResource(R.string.clear_search))
						}
					}
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
									val copiedName = stringResource(R.string.server_copy_name, server.name)
									ServerListRow(
										isSelectionMode = uiState.isSelectionMode,
										deleteDialogVisible = uiState.serverToDelete != null,
										onSwipeDelete = { viewModel.showDeleteConfirmDialog(server) }
									) {
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
										onPinClick = { viewModel.toggleServerPinned(server) },
										onCopyClick = { viewModel.duplicateServer(server, copiedName) },
										vendorMenu = vendorMenu(
											selectionMode = uiState.isSelectionMode,
											vendor = uiState.serverVendors[server.id]
										),
										onVendorAction = { entry -> viewModel.onVendorMenu(server.id, entry) },
										isConnecting = uiState.connectingServerId == server.id
									)
									}
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
									val copiedName = stringResource(R.string.server_copy_name, server.name)
									ServerListRow(
										isSelectionMode = uiState.isSelectionMode,
										deleteDialogVisible = uiState.serverToDelete != null,
										onSwipeDelete = { viewModel.showDeleteConfirmDialog(server) }
									) {
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
										onPinClick = { viewModel.toggleServerPinned(server) },
										onCopyClick = { viewModel.duplicateServer(server, copiedName) },
										vendorMenu = vendorMenu(
											selectionMode = uiState.isSelectionMode,
											vendor = uiState.serverVendors[server.id]
										),
										onVendorAction = { entry -> viewModel.onVendorMenu(server.id, entry) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListRow(
	isSelectionMode: Boolean,
	deleteDialogVisible: Boolean,
	onSwipeDelete: () -> Unit,
	content: @Composable () -> Unit
) {
	if (isSelectionMode) {
		content()
		return
	}
	val dialogVisible = rememberUpdatedState(deleteDialogVisible)
	val swipeDelete = rememberUpdatedState(onSwipeDelete)
	val dismissState = rememberSwipeToDismissBoxState(
		confirmValueChange = { value ->
			if (value == SwipeToDismissBoxValue.EndToStart) {
				if (!dialogVisible.value) {
					swipeDelete.value()
				}
				false
			} else {
				true
			}
		}
	)
	SwipeToDismissBox(
		state = dismissState,
		modifier = Modifier.clip(CardDefaults.shape),
		enableDismissFromStartToEnd = false,
		enableDismissFromEndToStart = true,
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.error),
				contentAlignment = Alignment.CenterEnd
			) {
				Icon(
					Icons.Default.Delete,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onError,
					modifier = Modifier.padding(horizontal = 20.dp)
				)
			}
		},
		content = { content() }
	)
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
	onPinClick: () -> Unit = {},
	onCopyClick: () -> Unit = {},
	vendorMenu: VendorMenu = vendorMenu(selectionMode = false, vendor = null),
	onVendorAction: (VendorMenuEntry) -> Unit = {},
	isConnecting: Boolean = false
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick, enabled = isSelectionMode || !isConnecting)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (isSelectionMode) {
				Icon(
					if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
					contentDescription = null,
					tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			ServerItemDetails(
				server = server,
				modifier = Modifier.weight(1f)
			)
			if (!isSelectionMode) {
				ServerItemActions(
					isPinned = server.isPinned,
					isConnecting = isConnecting,
					onConnectClick = onConnectClick,
					onEditClick = onEditClick,
					onDeleteClick = onDeleteClick,
					onPinClick = onPinClick,
					onCopyClick = onCopyClick,
					vendorMenu = vendorMenu,
					onVendorAction = onVendorAction
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerItemActions(
	isPinned: Boolean,
	isConnecting: Boolean,
	onConnectClick: () -> Unit,
	onEditClick: () -> Unit,
	onDeleteClick: () -> Unit,
	onPinClick: () -> Unit,
	onCopyClick: () -> Unit,
	vendorMenu: VendorMenu,
	onVendorAction: (VendorMenuEntry) -> Unit
) {
	var showMoreMenu by remember { mutableStateOf(false) }
	CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
		Row(
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(
				onClick = onConnectClick,
				enabled = !isConnecting,
				modifier = Modifier.size(36.dp)
			) {
				if (isConnecting) {
					CircularProgressIndicator(
						modifier = Modifier.size(20.dp),
						color = MaterialTheme.colorScheme.primary,
						strokeWidth = 3.dp,
						trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
					)
				} else {
					Icon(
						Icons.Default.PlayArrow,
						contentDescription = stringResource(R.string.connect),
						modifier = Modifier.size(22.dp)
					)
				}
			}
			IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
				Icon(
					Icons.Default.Edit,
					contentDescription = stringResource(R.string.edit),
					modifier = Modifier.size(22.dp)
				)
			}
			IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
				Icon(
					Icons.Default.Delete,
					contentDescription = stringResource(R.string.delete),
					modifier = Modifier.size(22.dp)
				)
			}
			Box {
				IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(36.dp)) {
					Icon(
						Icons.Default.MoreVert,
						contentDescription = stringResource(R.string.more_actions),
						modifier = Modifier.size(22.dp)
					)
				}
				DropdownMenu(
					expanded = showMoreMenu,
					onDismissRequest = { showMoreMenu = false }
				) {
					DropdownMenuItem(
						text = {
							Text(
								stringResource(
									if (isPinned) R.string.unpin_server else R.string.pin_server
								)
							)
						},
						onClick = {
							showMoreMenu = false
							onPinClick()
						}
					)
					DropdownMenuItem(
						text = { Text(stringResource(R.string.copy_server)) },
						onClick = {
							showMoreMenu = false
							onCopyClick()
						}
					)
					vendorMenu.entries.forEach { entry ->
						DropdownMenuItem(
							text = { Text(vendorMenuLabel(vendorMenu.vendor, entry)) },
							onClick = {
								showMoreMenu = false
								onVendorAction(entry)
							}
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ServerItemDetails(
	server: Server,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Text(
			text = server.name,
			style = MaterialTheme.typography.titleMedium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth()
		)
		Text(
			text = "${server.host}:${server.port}",
			style = MaterialTheme.typography.bodyMedium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth()
		)
		Text(
			text = server.username,
			style = MaterialTheme.typography.bodySmall,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth()
		)
		server.systemVersion?.let { systemVersion ->
			Text(
				text = systemVersion,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.primary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}

@Composable
private fun listErrorMessage(error: AppError): String {
	return when (error.message) {
		"JUMP_HOST_IN_USE" -> stringResource(R.string.error_jump_host_in_use)
		"JUMP_HOST_MISSING" -> stringResource(R.string.error_jump_host_missing)
		"JUMP_HOST_FAILED" -> stringResource(R.string.error_jump_host_failed)
		"JUMP_ALGORITHM_NEGOTIATION_FAILED" -> stringResource(R.string.error_jump_algorithm_negotiation)
		"TARGET_CONNECT_FAILED" -> stringResource(R.string.error_target_connect_failed)
		"SSH_ALGORITHM_NEGOTIATION_FAILED" -> stringResource(R.string.error_ssh_algorithm_negotiation)
		"CONNECTION_FAILED" -> stringResource(R.string.connection_failed)
		else -> error.message
	}
}

@Composable
private fun vendorMenuLabel(vendor: VendorKind?, entry: VendorMenuEntry): String {
	val vendorName = when (vendor) {
		VendorKind.BANDWAGON -> stringResource(R.string.vendor_bandwagon)
		VendorKind.CLOUDCONE -> stringResource(R.string.vendor_cloudcone)
		VendorKind.VIRTUALIZOR -> stringResource(R.string.vendor_virtualizor)
		null -> ""
	}
	return when (entry) {
		VendorMenuEntry.BIND -> stringResource(R.string.vendor_bind)
		VendorMenuEntry.START -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_start))
		VendorMenuEntry.STOP -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_stop))
		VendorMenuEntry.RESTART -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_restart))
		VendorMenuEntry.STATUS -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_status))
		VendorMenuEntry.SERVICE_INFO -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_service_info))
		VendorMenuEntry.MIGRATE -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_migrate))
		VendorMenuEntry.BANDWIDTH -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.virtualizor_bandwidth))
		VendorMenuEntry.UNBIND -> stringResource(R.string.vendor_labeled_action, vendorName, stringResource(R.string.bandwagon_unbind))
	}
}

@Composable
private fun VendorPromptDialog(
	prompt: VendorPrompt?,
	viewModel: ServerListViewModel
) {
	when (prompt) {
		null -> Unit
		is VendorPrompt.Choose -> {
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.vendor_choose_title)) },
				text = {
					Column {
						TextButton(onClick = { viewModel.chooseVendor(VendorKind.BANDWAGON) }) {
							Text(stringResource(R.string.vendor_bandwagon))
						}
						TextButton(onClick = { viewModel.chooseVendor(VendorKind.CLOUDCONE) }) {
							Text(stringResource(R.string.vendor_cloudcone))
						}
						TextButton(onClick = { viewModel.chooseVendor(VendorKind.VIRTUALIZOR) }) {
							Text(stringResource(R.string.vendor_virtualizor))
						}
					}
				},
				confirmButton = {},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.Bandwagon -> {
			var veid by remember(prompt.serverId) { mutableStateOf("") }
			var apiKey by remember(prompt.serverId) { mutableStateOf("") }
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.bandwagon_bind_title)) },
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = veid,
							onValueChange = { veid = it },
							label = { Text(stringResource(R.string.bandwagon_veid)) },
							singleLine = true
						)
						OutlinedTextField(
							value = apiKey,
							onValueChange = { apiKey = it },
							label = { Text(stringResource(R.string.bandwagon_api_key)) },
							singleLine = true,
							visualTransformation = PasswordVisualTransformation()
						)
					}
				},
				confirmButton = {
					TextButton(
						onClick = { viewModel.submitBandwagonBind(veid, apiKey) },
						enabled = veid.isNotBlank() && apiKey.isNotBlank()
					) {
						Text(stringResource(R.string.confirm))
					}
				},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.CloudconeSecrets -> {
			var apiKey by remember(prompt.serverId) { mutableStateOf("") }
			var hash by remember(prompt.serverId) { mutableStateOf("") }
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.cloudcone_account_title)) },
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = apiKey,
							onValueChange = { apiKey = it },
							label = { Text(stringResource(R.string.cloudcone_api_key)) },
							singleLine = true,
							visualTransformation = PasswordVisualTransformation()
						)
						OutlinedTextField(
							value = hash,
							onValueChange = { hash = it },
							label = { Text(stringResource(R.string.cloudcone_hash)) },
							singleLine = true,
							visualTransformation = PasswordVisualTransformation()
						)
					}
				},
				confirmButton = {
					TextButton(
						onClick = { viewModel.submitCloudconeAccount(apiKey, hash) },
						enabled = apiKey.isNotBlank() && hash.isNotBlank()
					) {
						Text(stringResource(R.string.confirm))
					}
				},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.VirtualizorSecrets -> {
			var host by remember(prompt.serverId) { mutableStateOf("") }
			var port by remember(prompt.serverId) { mutableStateOf("4083") }
			var apiKey by remember(prompt.serverId) { mutableStateOf("") }
			var apiPass by remember(prompt.serverId) { mutableStateOf("") }
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.virtualizor_account_title)) },
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						if (prompt.pendingPin != null) {
							Text(stringResource(R.string.virtualizor_pin_confirm, prompt.pendingPin))
						} else {
							OutlinedTextField(
								value = host,
								onValueChange = { host = it },
								label = { Text(stringResource(R.string.virtualizor_host)) },
								singleLine = true
							)
							OutlinedTextField(
								value = port,
								onValueChange = { port = it },
								label = { Text(stringResource(R.string.virtualizor_port)) },
								singleLine = true
							)
							OutlinedTextField(
								value = apiKey,
								onValueChange = { apiKey = it },
								label = { Text(stringResource(R.string.virtualizor_api_key)) },
								singleLine = true,
								visualTransformation = PasswordVisualTransformation()
							)
							OutlinedTextField(
								value = apiPass,
								onValueChange = { apiPass = it },
								label = { Text(stringResource(R.string.virtualizor_api_pass)) },
								singleLine = true,
								visualTransformation = PasswordVisualTransformation()
							)
						}
					}
				},
				confirmButton = {
					TextButton(
						onClick = {
							viewModel.submitVirtualizorAccount(host, port, apiKey, apiPass, prompt.pendingPin)
						},
						enabled = prompt.pendingPin != null || (host.isNotBlank() && apiKey.isNotBlank() && apiPass.isNotBlank())
					) {
						Text(stringResource(R.string.confirm))
					}
				},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.VirtualizorPick -> {
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.virtualizor_pick_instance)) },
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						if (prompt.instances.isEmpty()) {
							Text(stringResource(R.string.virtualizor_no_free_instance))
						}
						prompt.instances.forEach { instance ->
							TextButton(onClick = { viewModel.bindVirtualizorInstance(instance.id) }) {
								val address = instance.address
								Text(
									if (address.isNullOrBlank()) instance.id else "${instance.id} · $address"
								)
							}
						}
					}
				},
				confirmButton = {},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.CloudconePick -> {
			AlertDialog(
				onDismissRequest = { viewModel.dismissVendorPrompt() },
				title = { Text(stringResource(R.string.cloudcone_pick_instance)) },
				text = {
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						if (prompt.instances.isEmpty()) {
							Text(stringResource(R.string.cloudcone_no_free_instance))
						}
						prompt.instances.forEach { instance ->
							TextButton(onClick = { viewModel.bindCloudconeInstance(instance.id) }) {
								val address = instance.address
								Text(
									if (address.isNullOrBlank()) instance.id else "${instance.id} · $address"
								)
							}
						}
					}
				},
				confirmButton = {},
				dismissButton = {
					TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		}
		is VendorPrompt.ConfirmStop -> BandwagonConfirmDialog(
			message = stringResource(R.string.vendor_confirm_stop, vendorName(prompt.vendor)),
			onConfirm = { viewModel.confirmVendorPower() },
			onDismiss = { viewModel.dismissVendorPrompt() }
		)
		is VendorPrompt.ConfirmRestart -> BandwagonConfirmDialog(
			message = stringResource(R.string.vendor_confirm_restart, vendorName(prompt.vendor)),
			onConfirm = { viewModel.confirmVendorPower() },
			onDismiss = { viewModel.dismissVendorPrompt() }
		)
		is VendorPrompt.ConfirmUnbind -> BandwagonConfirmDialog(
			message = stringResource(
				when (prompt.vendor) {
					VendorKind.CLOUDCONE -> R.string.cloudcone_confirm_unbind
					VendorKind.VIRTUALIZOR -> R.string.virtualizor_confirm_unbind
					VendorKind.BANDWAGON -> R.string.bandwagon_confirm_unbind
				}
			),
			onConfirm = { viewModel.confirmVendorUnbind() },
			onDismiss = { viewModel.dismissVendorPrompt() }
		)
		is VendorPrompt.Status -> BandwagonConfirmDialog(
			message = stringResource(R.string.bandwagon_status_value, prompt.text),
			onConfirm = { viewModel.dismissVendorPrompt() },
			onDismiss = { viewModel.dismissVendorPrompt() },
			confirmOnly = true
		)
		VendorPrompt.StatusUnknown -> BandwagonConfirmDialog(
			message = stringResource(R.string.bandwagon_status_unknown),
			onConfirm = { viewModel.dismissVendorPrompt() },
			onDismiss = { viewModel.dismissVendorPrompt() },
			confirmOnly = true
		)
		is VendorPrompt.MigratePick -> AlertDialog(
			onDismissRequest = { viewModel.dismissVendorPrompt() },
			title = { Text(stringResource(R.string.bandwagon_migrate_title)) },
			text = {
				BandwagonMigrateLocationList(
					locations = prompt.locations,
					onPick = { viewModel.pickMigrateLocation(it) }
				)
			},
			confirmButton = {},
			dismissButton = {
				TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
		is VendorPrompt.ConfirmMigrate -> BandwagonConfirmDialog(
			message = stringResource(
				R.string.bandwagon_migrate_confirm,
				prompt.location.description?.takeIf { it.isNotBlank() } ?: prompt.location.id
			),
			onConfirm = { viewModel.confirmMigrate() },
			onDismiss = { viewModel.dismissVendorPrompt() }
		)
		is VendorPrompt.MigrateResult -> AlertDialog(
			onDismissRequest = { viewModel.dismissVendorPrompt() },
			title = { Text(stringResource(R.string.bandwagon_migrate_title)) },
			text = {
				BandwagonMigrateResultPanel(
					start = prompt.start,
					onUseHost = { viewModel.applyMigrateHost(it) }
				)
			},
			confirmButton = {
				TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
					Text(stringResource(R.string.confirm))
				}
			}
		)
		is VendorPrompt.ServiceInfo -> AlertDialog(
			onDismissRequest = { viewModel.dismissVendorPrompt() },
			text = { BandwagonServiceInfoPanel(prompt.info) },
			confirmButton = {
				TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
					Text(stringResource(R.string.confirm))
				}
			}
		)
		is VendorPrompt.Bandwidth -> AlertDialog(
			onDismissRequest = { viewModel.dismissVendorPrompt() },
			text = { VirtualizorBandwidthPanel(prompt.bandwidth) },
			confirmButton = {
				TextButton(onClick = { viewModel.dismissVendorPrompt() }) {
					Text(stringResource(R.string.confirm))
				}
			}
		)
	}
}

@Composable
private fun vendorName(vendor: VendorKind): String {
	return stringResource(
		when (vendor) {
			VendorKind.CLOUDCONE -> R.string.vendor_cloudcone
			VendorKind.VIRTUALIZOR -> R.string.vendor_virtualizor
			VendorKind.BANDWAGON -> R.string.vendor_bandwagon
		}
	)
}

@Composable
private fun BandwagonConfirmDialog(
	message: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
	confirmOnly: Boolean = false
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		text = { Text(message) },
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(stringResource(R.string.confirm))
			}
		},
		dismissButton = if (confirmOnly) {
			null
		} else {
			{
				TextButton(onClick = onDismiss) {
					Text(stringResource(R.string.cancel))
				}
			}
		}
	)
}
