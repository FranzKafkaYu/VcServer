package com.franzkafkayu.vcserver.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.CustomShellCommand
import com.franzkafkayu.vcserver.models.CustomShellCommandScope
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.ui.viewmodels.CustomShellCommandsUiState
import com.franzkafkayu.vcserver.ui.viewmodels.CustomShellCommandsViewModel
import com.franzkafkayu.vcserver.utils.CustomShellCommands

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomShellCommandsScreen(
	viewModel: CustomShellCommandsViewModel,
	onBackClick: () -> Unit
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current
	var showEditor by remember { mutableStateOf(false) }
	var editorId by remember { mutableStateOf<String?>(null) }
	var title by remember { mutableStateOf("") }
	var body by remember { mutableStateOf("") }
	var scopeType by remember { mutableStateOf(CustomShellCommandScope.GLOBAL) }
	var scopeId by remember { mutableStateOf<Long?>(null) }
	var pendingDelete by remember { mutableStateOf<CustomShellCommand?>(null) }

	fun openEditor(command: CustomShellCommand?) {
		editorId = command?.id
		title = command?.title.orEmpty()
		body = command?.body.orEmpty()
		scopeType = CustomShellCommandScope.normalize(command?.scopeType)
		scopeId = command?.scopeId
		showEditor = true
	}

	LaunchedEffect(uiState.errorMessage) {
		uiState.errorMessage?.let { error ->
			snackbarHostState.showSnackbar(
				message = localizeCustomShellError(context, error),
				duration = SnackbarDuration.Short
			)
			viewModel.clearError()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.custom_shell_commands)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
					}
				},
				actions = {
					IconButton(onClick = { openEditor(null) }) {
						Icon(Icons.Default.Add, contentDescription = stringResource(R.string.custom_shell_command_add))
					}
				}
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) }
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			if (uiState.commands.isEmpty()) {
				Text(
					text = stringResource(R.string.custom_shell_command_empty),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				uiState.commands.forEach { command ->
					CommandRow(
						command = command,
						uiState = uiState,
						onEdit = { openEditor(command) },
						onDelete = { pendingDelete = command }
					)
				}
			}
		}
	}

	pendingDelete?.let { command ->
		AlertDialog(
			onDismissRequest = { pendingDelete = null },
			title = { Text(stringResource(R.string.custom_shell_command_delete)) },
			text = {
				Text(stringResource(R.string.custom_shell_command_delete_confirm, command.title))
			},
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.deleteCommand(command.id)
						pendingDelete = null
					}
				) {
					Text(
						stringResource(R.string.confirm),
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

	if (showEditor) {
		AlertDialog(
			onDismissRequest = { showEditor = false },
			title = {
				Text(
					stringResource(
						if (editorId == null) R.string.custom_shell_command_add else R.string.custom_shell_command_edit
					)
				)
			},
			text = {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					OutlinedTextField(
						value = title,
						onValueChange = { title = it },
						label = { Text(stringResource(R.string.custom_shell_command_name)) },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					OutlinedTextField(
						value = body,
						onValueChange = { body = it },
						label = { Text(stringResource(R.string.custom_shell_command_body)) },
						minLines = 3,
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = stringResource(R.string.custom_shell_scope),
						style = MaterialTheme.typography.labelLarge
					)
					ScopeOption(
						selected = scopeType == CustomShellCommandScope.GLOBAL,
						label = stringResource(R.string.custom_shell_scope_global),
						onClick = {
							scopeType = CustomShellCommandScope.GLOBAL
							scopeId = null
						}
					)
					ScopeOption(
						selected = scopeType == CustomShellCommandScope.GROUP,
						label = stringResource(R.string.custom_shell_scope_group),
						onClick = {
							scopeType = CustomShellCommandScope.GROUP
							if (uiState.groups.none { it.id == scopeId }) {
								scopeId = null
							}
						}
					)
					ScopeOption(
						selected = scopeType == CustomShellCommandScope.SERVER,
						label = stringResource(R.string.custom_shell_scope_server),
						onClick = {
							scopeType = CustomShellCommandScope.SERVER
							if (uiState.servers.none { it.id == scopeId }) {
								scopeId = null
							}
						}
					)
					if (scopeType == CustomShellCommandScope.GROUP) {
						TargetPicker(
							label = stringResource(R.string.custom_shell_scope_select_group),
							value = uiState.groups.find { it.id == scopeId }?.name
								?: stringResource(R.string.custom_shell_scope_select_group),
							options = uiState.groups.map { it.id to it.name },
							onSelect = { scopeId = it }
						)
					}
					if (scopeType == CustomShellCommandScope.SERVER) {
						TargetPicker(
							label = stringResource(R.string.custom_shell_scope_select_server),
							value = uiState.servers.find { it.id == scopeId }?.let { serverLabel(it) }
								?: stringResource(R.string.custom_shell_scope_select_server),
							options = uiState.servers.map { it.id to serverLabel(it) },
							onSelect = { scopeId = it }
						)
					}
				}
			},
			confirmButton = {
				TextButton(
					onClick = {
						if (viewModel.saveCommand(editorId, title, body, scopeType, scopeId)) {
							showEditor = false
						}
					}
				) {
					Text(stringResource(R.string.save))
				}
			},
			dismissButton = {
				TextButton(onClick = { showEditor = false }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandRow(
	command: CustomShellCommand,
	uiState: CustomShellCommandsUiState,
	onEdit: () -> Unit,
	onDelete: () -> Unit
) {
	val missing = CustomShellCommands.isTargetMissing(
		command,
		uiState.groups.map { it.id }.toSet(),
		uiState.servers.map { it.id }.toSet()
	)
	Card {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = command.title,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					style = MaterialTheme.typography.titleMedium
				)
				Text(
					text = command.body,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Text(
					text = if (missing) {
						stringResource(R.string.custom_shell_scope_invalid_target)
					} else {
						scopeSummary(command, uiState.groups, uiState.servers)
					},
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					style = MaterialTheme.typography.bodySmall,
					color = if (missing) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					}
				)
			}
			CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
				Row {
					IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
						Icon(
							Icons.Default.Edit,
							contentDescription = stringResource(R.string.custom_shell_command_edit),
							modifier = Modifier.size(22.dp)
						)
					}
					IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
						Icon(
							Icons.Default.Delete,
							contentDescription = stringResource(R.string.custom_shell_command_delete),
							modifier = Modifier.size(22.dp)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ScopeOption(
	selected: Boolean,
	label: String,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.CenterVertically
	) {
		RadioButton(selected = selected, onClick = onClick)
		Text(text = label)
	}
}

@Composable
private fun TargetPicker(
	label: String,
	value: String,
	options: List<Pair<Long, String>>,
	onSelect: (Long) -> Unit
) {
	var showPicker by remember { mutableStateOf(false) }
	Column {
		Box(modifier = Modifier.clickable { showPicker = true }) {
			OutlinedTextField(
				value = value,
				onValueChange = { },
				readOnly = true,
				enabled = false,
				modifier = Modifier.fillMaxWidth(),
				trailingIcon = {
					Icon(Icons.Default.ArrowDropDown, contentDescription = label)
				},
				label = { Text(label) },
				colors = OutlinedTextFieldDefaults.colors(
					disabledTextColor = MaterialTheme.colorScheme.onSurface,
					disabledBorderColor = MaterialTheme.colorScheme.outline,
					disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
					disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
				)
			)
		}
	}
	if (showPicker) {
		AlertDialog(
			onDismissRequest = { showPicker = false },
			title = { Text(label) },
			text = {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 360.dp)
						.verticalScroll(rememberScrollState())
				) {
					options.forEach { (id, name) ->
						DropdownMenuItem(
							text = { Text(name) },
							onClick = {
								onSelect(id)
								showPicker = false
							}
						)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showPicker = false }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}
}

@Composable
private fun scopeSummary(
	command: CustomShellCommand,
	groups: List<ServerGroup>,
	servers: List<Server>
): String {
	return when (CustomShellCommandScope.normalize(command.scopeType)) {
		CustomShellCommandScope.GROUP -> {
			val name = groups.find { it.id == command.scopeId }?.name.orEmpty()
			stringResource(R.string.custom_shell_scope_group_summary, name)
		}
		CustomShellCommandScope.SERVER -> {
			val server = servers.find { it.id == command.scopeId }
			stringResource(
				R.string.custom_shell_scope_server_summary,
				server?.let { serverLabel(it) }.orEmpty()
			)
		}
		else -> stringResource(R.string.custom_shell_scope_global)
	}
}

private fun serverLabel(server: Server): String {
	return "${server.name} (${server.host}:${server.port})"
}

private fun localizeCustomShellError(context: Context, errorKey: String): String {
	return when {
		errorKey == "CUSTOM_SHELL_COMMAND_INVALID" ->
			context.getString(R.string.custom_shell_command_invalid)
		errorKey == "CUSTOM_SHELL_COMMAND_SCOPE_INVALID" ->
			context.getString(R.string.custom_shell_command_scope_invalid)
		errorKey.startsWith("UPDATE_CUSTOM_SHELL_COMMANDS_FAILED:") ->
			errorKey.substringAfter(":")
		else -> errorKey
	}
}
