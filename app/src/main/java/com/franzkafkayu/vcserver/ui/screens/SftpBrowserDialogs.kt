package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.services.SftpRemoteEntry
import com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringUiState

@Composable
fun SftpBrowserDialog(
	uiState: ServerMonitoringUiState,
	onDismiss: () -> Unit,
	onNavigateUp: () -> Unit,
	onOpenEntry: (SftpRemoteEntry) -> Unit,
	onUpload: () -> Unit,
	onDownload: (SftpRemoteEntry) -> Unit,
	onRetry: () -> Unit,
	onCreateFile: (String) -> Unit,
	onCreateFolder: (String) -> Unit,
	onDelete: (SftpRemoteEntry) -> Unit,
	onRename: (SftpRemoteEntry, String) -> Unit
) {
	var createMode by remember { mutableStateOf<SftpCreateMode?>(null) }
	var pendingDelete by remember { mutableStateOf<SftpRemoteEntry?>(null) }
	var pendingRename by remember { mutableStateOf<SftpRemoteEntry?>(null) }
	var showPageMenu by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
		modifier = Modifier.fillMaxWidth(0.95f),
		title = { Text(stringResource(R.string.sftp_file_transfer)) },
		text = {
			Column(
				modifier = Modifier.heightIn(min = 240.dp, max = 480.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					IconButton(
						onClick = onNavigateUp,
						enabled = uiState.sftpCurrentPath.isNotEmpty() && uiState.sftpCurrentPath != "/"
					) {
						Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.sftp_parent_dir))
					}
					Text(
						text = uiState.sftpCurrentPath.ifBlank { "…" },
						style = MaterialTheme.typography.bodyMedium,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f)
					)
					Box {
						IconButton(onClick = { showPageMenu = true }) {
							Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sftp_more))
						}
						DropdownMenu(
							expanded = showPageMenu,
							onDismissRequest = { showPageMenu = false }
						) {
							DropdownMenuItem(
								text = { Text(stringResource(R.string.sftp_new_file)) },
								leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
								onClick = {
									showPageMenu = false
									createMode = SftpCreateMode.File
								}
							)
							DropdownMenuItem(
								text = { Text(stringResource(R.string.sftp_new_folder)) },
								leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
								onClick = {
									showPageMenu = false
									createMode = SftpCreateMode.Folder
								}
							)
							DropdownMenuItem(
								text = { Text(stringResource(R.string.sftp_upload)) },
								leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
								onClick = {
									showPageMenu = false
									onUpload()
								}
							)
						}
					}
				}
				uiState.sftpBrowseError?.let { err ->
					Text(
						text = sftpBrowseErrorText(err),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
					TextButton(onClick = onRetry) {
						Text(stringResource(R.string.sftp_retry))
					}
				}
				when {
					uiState.sftpListing || uiState.sftpEditorLoading -> {
						CircularProgressIndicator(modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally))
					}
					else -> {
						LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
							items(uiState.sftpEntries, key = { it.name }) { entry ->
								SftpEntryRow(
									entry = entry,
									onOpen = { onOpenEntry(entry) },
									onEdit = { onOpenEntry(entry) },
									onDownload = { onDownload(entry) },
									onRename = { pendingRename = entry },
									onDelete = { pendingDelete = entry }
								)
							}
						}
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel))
			}
		}
	)

	createMode?.let { mode ->
		SftpNameInputDialog(
			title = stringResource(
				if (mode == SftpCreateMode.File) R.string.sftp_new_file else R.string.sftp_new_folder
			),
			onConfirm = { name ->
				createMode = null
				if (mode == SftpCreateMode.File) onCreateFile(name) else onCreateFolder(name)
			},
			onDismiss = { createMode = null }
		)
	}

	pendingRename?.let { entry ->
		SftpNameInputDialog(
			title = stringResource(R.string.sftp_rename),
			initialName = entry.name,
			onConfirm = { name ->
				pendingRename = null
				onRename(entry, name)
			},
			onDismiss = { pendingRename = null }
		)
	}

	pendingDelete?.let { entry ->
		val full = joinBrowserPath(uiState.sftpCurrentPath, entry.name)
		AlertDialog(
			onDismissRequest = { pendingDelete = null },
			title = { Text(stringResource(R.string.sftp_delete_title)) },
			text = {
				Text(
					stringResource(
						if (entry.isDirectory) R.string.sftp_delete_folder_message
						else R.string.sftp_delete_file_message,
						full
					)
				)
			},
			confirmButton = {
				TextButton(onClick = {
					pendingDelete = null
					onDelete(entry)
				}) {
					Text(
						stringResource(R.string.sftp_delete),
						color = MaterialTheme.colorScheme.error
					)
				}
			},
			dismissButton = {
				TextButton(onClick = { pendingDelete = null }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}
}

private enum class SftpCreateMode { File, Folder }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SftpEntryRow(
	entry: SftpRemoteEntry,
	onOpen: () -> Unit,
	onEdit: () -> Unit,
	onDownload: () -> Unit,
	onRename: () -> Unit,
	onDelete: () -> Unit
) {
	var showMenu by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = onOpen,
				onLongClick = { showMenu = true }
			)
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Icon(
			if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary
		)
		Column(modifier = Modifier.weight(1f)) {
			Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (!entry.isDirectory) {
				Text(
					text = formatSftpSize(entry.size),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		Box {
			IconButton(onClick = { showMenu = true }) {
				Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sftp_more))
			}
			DropdownMenu(
				expanded = showMenu,
				onDismissRequest = { showMenu = false }
			) {
				if (!entry.isDirectory) {
					DropdownMenuItem(
						text = { Text(stringResource(R.string.edit)) },
						leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
						onClick = {
							showMenu = false
							onEdit()
						}
					)
					DropdownMenuItem(
						text = { Text(stringResource(R.string.sftp_download)) },
						leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
						onClick = {
							showMenu = false
							onDownload()
						}
					)
				}
				DropdownMenuItem(
					text = { Text(stringResource(R.string.sftp_rename)) },
					leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
					onClick = {
						showMenu = false
						onRename()
					}
				)
				Divider()
				DropdownMenuItem(
					text = {
						Text(
							stringResource(R.string.sftp_delete),
							color = MaterialTheme.colorScheme.error
						)
					},
					leadingIcon = {
						Icon(
							Icons.Default.Delete,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error
						)
					},
					onClick = {
						showMenu = false
						onDelete()
					}
				)
			}
		}
	}
}

@Composable
private fun SftpNameInputDialog(
	title: String,
	initialName: String = "",
	onConfirm: (String) -> Unit,
	onDismiss: () -> Unit
) {
	var name by remember { mutableStateOf(initialName) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			OutlinedTextField(
				value = name,
				onValueChange = { name = it },
				label = { Text(stringResource(R.string.sftp_name_hint)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)
		},
		confirmButton = {
			TextButton(
				onClick = { onConfirm(name.trim()) },
				enabled = name.trim().isNotEmpty() && name.trim() != initialName
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

private fun joinBrowserPath(dir: String, name: String): String {
	val base = dir.trimEnd('/')
	return if (base.isEmpty() || base == "/") "/$name" else "$base/$name"
}

@Composable
fun SftpEditorDialog(
	uiState: ServerMonitoringUiState,
	onContentChange: (String) -> Unit,
	onSave: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
		modifier = Modifier.fillMaxWidth(0.95f),
		title = {
			Text(
				text = uiState.sftpEditorPath,
				style = MaterialTheme.typography.titleSmall,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		},
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				uiState.sftpEditorError?.let { err ->
					Text(
						text = sftpBrowseErrorText(err),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
				OutlinedTextField(
					value = uiState.sftpEditorContent,
					onValueChange = onContentChange,
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 200.dp, max = 400.dp),
					enabled = !uiState.sftpEditorSaving
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = onSave,
				enabled = !uiState.sftpEditorSaving && uiState.sftpEditorContent != uiState.sftpEditorBaseline
			) {
				if (uiState.sftpEditorSaving) {
					CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
				} else {
					Text(stringResource(R.string.sftp_save))
				}
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !uiState.sftpEditorSaving) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}

@Composable
fun SftpDiscardConfirmDialog(
	onConfirm: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.sftp_discard_title)) },
		text = { Text(stringResource(R.string.sftp_discard_message)) },
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(stringResource(R.string.sftp_discard_confirm))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}

@Composable
private fun sftpBrowseErrorText(errorKey: String): String {
	val key = errorKey.split(":", limit = 2)[0]
	return when (key) {
		"SESSION_NOT_CONNECTED" -> stringResource(R.string.error_sftp_session_not_connected)
		"UNABLE_TO_CREATE_SFTP_CHANNEL" -> stringResource(R.string.error_sftp_unable_to_create_channel)
		"PERMISSION_DENIED" -> stringResource(R.string.error_sftp_permission_denied)
		"FILE_NOT_EXISTS" -> stringResource(R.string.error_sftp_file_not_exists)
		"PARENT_DIR_NOT_EXISTS" -> stringResource(R.string.error_sftp_parent_dir_not_exists)
		"FILE_TOO_LARGE" -> stringResource(R.string.error_sftp_file_too_large)
		"FILE_BINARY" -> stringResource(R.string.error_sftp_file_binary)
		"SFTP_SERVICE_NOT_AVAILABLE" -> stringResource(R.string.error_sftp_service_not_available)
		"NAME_EXISTS" -> stringResource(R.string.error_sftp_name_exists)
		"NAME_INVALID" -> stringResource(R.string.error_sftp_name_invalid)
		else -> stringResource(R.string.error_sftp_generic)
	}
}

private fun formatSftpSize(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
	if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
	return "${bytes / (1024 * 1024 * 1024)} GB"
}
