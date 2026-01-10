package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.ui.viewmodels.ServerGroupManagementViewModel
import com.franzkafkayu.vcserver.utils.AppError

/**
 * 分组管理对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerGroupManagementDialog(
	viewModel: ServerGroupManagementViewModel,
	onDismiss: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.manage_groups)) },
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				// 分组列表
				if (uiState.groups.isEmpty()) {
					Text(
						text = stringResource(R.string.no_groups),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				} else {
					uiState.groups.forEach { group ->
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = group.name,
								style = MaterialTheme.typography.bodyLarge,
								modifier = Modifier.weight(1f)
							)
							Row {
								IconButton(onClick = { viewModel.showEditGroupDialog(group) }) {
									Icon(
										Icons.Default.Edit,
										contentDescription = stringResource(R.string.edit_group)
									)
								}
								IconButton(onClick = { viewModel.showDeleteConfirmDialog(group) }) {
									Icon(
										Icons.Default.Delete,
										contentDescription = stringResource(R.string.delete_group),
										tint = MaterialTheme.colorScheme.error
									)
								}
							}
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = { viewModel.showCreateGroupDialog() }) {
				Icon(Icons.Default.Add, contentDescription = null)
				Spacer(Modifier.width(4.dp))
				Text(stringResource(R.string.create_group))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.done))
			}
		}
	)

	// 创建分组对话框
	if (uiState.isCreateGroupDialogVisible) {
		CreateGroupDialog(
			viewModel = viewModel,
			onDismiss = { viewModel.hideCreateGroupDialog() }
		)
	}

	// 编辑分组对话框
	if (uiState.isEditGroupDialogVisible && uiState.editingGroup != null) {
		EditGroupDialog(
			viewModel = viewModel,
			group = uiState.editingGroup,
			onDismiss = { viewModel.hideEditGroupDialog() }
		)
	}

	// 删除确认对话框
	uiState.groupToDelete?.let { group ->
		AlertDialog(
			onDismissRequest = { viewModel.cancelDelete() },
			title = { Text(stringResource(R.string.delete_group)) },
			text = {
				Text(stringResource(R.string.delete_group_confirm, group.name))
			},
			confirmButton = {
				TextButton(
					onClick = { viewModel.confirmDeleteGroup() }
				) {
					Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.cancelDelete() }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}
}

/**
 * 创建分组对话框
 */
@Composable
private fun CreateGroupDialog(
	viewModel: ServerGroupManagementViewModel,
	onDismiss: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	var groupName by remember { mutableStateOf("") }
	
	// 当对话框打开时，同步 groupName 与 uiState.groupNameInput
	LaunchedEffect(uiState.isCreateGroupDialogVisible, uiState.groupNameInput) {
		if (uiState.isCreateGroupDialogVisible) {
			// 如果 uiState.groupNameInput 为空，说明是新打开的对话框，重置输入
			if (uiState.groupNameInput.isEmpty()) {
				groupName = ""
			} else {
				// 否则同步 ViewModel 中的输入（用于编辑场景）
				groupName = uiState.groupNameInput
			}
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.create_group)) },
		text = {
			Column {
				OutlinedTextField(
					value = groupName,
					onValueChange = { 
						groupName = it
						viewModel.updateGroupNameInput(it)
					},
					label = { Text(stringResource(R.string.group_name)) },
					placeholder = { Text(stringResource(R.string.group_name_hint)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
					isError = uiState.error != null
				)
				uiState.error?.let { error ->
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = getErrorLocalizedMessage(error),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = { 
					viewModel.createGroup()
					// 使用 LaunchedEffect 监听成功状态
				},
				enabled = groupName.trim().isNotEmpty() && !uiState.isLoading
			) {
				Text(stringResource(R.string.confirm))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}

/**
 * 获取错误消息的本地化字符串
 */
@Composable
private fun getErrorLocalizedMessage(error: AppError): String {
	return when (error) {
		is AppError.ValidationError -> {
			when (error.message) {
				"GROUP_NAME_EMPTY" -> stringResource(R.string.error_group_name_empty)
				"GROUP_NAME_EXISTS" -> stringResource(R.string.error_group_name_exists)
				else -> error.message
			}
		}
		is AppError.DatabaseError -> {
			if (error.message.contains("UNIQUE constraint", ignoreCase = true)) {
				stringResource(R.string.error_group_name_exists)
			} else {
				error.message
			}
		}
		else -> error.message
	}
}

/**
 * 编辑分组对话框
 */
@Composable
private fun EditGroupDialog(
	viewModel: ServerGroupManagementViewModel,
	group: ServerGroup,
	onDismiss: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	var groupName by remember(group.id) { mutableStateOf(group.name) }

	LaunchedEffect(uiState.groupNameInput) {
		if (uiState.groupNameInput.isNotEmpty()) {
			groupName = uiState.groupNameInput
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.edit_group)) },
		text = {
			Column {
				OutlinedTextField(
					value = groupName,
					onValueChange = { 
						groupName = it
						viewModel.updateGroupNameInput(it)
					},
					label = { Text(stringResource(R.string.group_name)) },
					placeholder = { Text(stringResource(R.string.group_name_hint)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
					isError = uiState.error != null
				)
				uiState.error?.let { error ->
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = getErrorLocalizedMessage(error),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = { 
					viewModel.updateGroup()
					if (uiState.error == null) {
						onDismiss()
					}
				},
				enabled = groupName.trim().isNotEmpty() && !uiState.isLoading
			) {
				Text(stringResource(R.string.confirm))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}
