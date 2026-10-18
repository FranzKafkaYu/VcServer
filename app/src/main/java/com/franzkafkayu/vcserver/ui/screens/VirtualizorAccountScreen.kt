package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.ui.viewmodels.VirtualizorAccountViewModel
import com.franzkafkayu.vcserver.ui.viewmodels.VirtualizorInstanceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualizorAccountScreen(
	viewModel: VirtualizorAccountViewModel,
	onBackClick: () -> Unit,
	onAddServer: (vpsid: String, host: String?) -> Unit,
	onOpenServer: (Long) -> Unit
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current

	LaunchedEffect(uiState.noticeMessageRes, uiState.noticeMessageArg) {
		val res = uiState.noticeMessageRes ?: return@LaunchedEffect
		val text = if (uiState.noticeMessageArg != null) {
			context.getString(res, uiState.noticeMessageArg)
		} else {
			context.getString(res)
		}
		snackbarHostState.showSnackbar(text)
		viewModel.consumeNotice()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.virtualizor_account_title)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
					}
				}
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) }
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			if (uiState.account == null) {
				Text(stringResource(R.string.virtualizor_account_empty))
			} else {
				Text("${uiState.account?.host}:${uiState.account?.port}")
			}
			OutlinedTextField(
				value = uiState.host,
				onValueChange = viewModel::updateHost,
				label = { Text(stringResource(R.string.virtualizor_host)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)
			OutlinedTextField(
				value = uiState.port,
				onValueChange = viewModel::updatePort,
				label = { Text(stringResource(R.string.virtualizor_port)) },
				singleLine = true,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
				modifier = Modifier.fillMaxWidth()
			)
			OutlinedTextField(
				value = uiState.apiKey,
				onValueChange = viewModel::updateApiKey,
				label = { Text(stringResource(R.string.virtualizor_api_key)) },
				singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				modifier = Modifier.fillMaxWidth()
			)
			OutlinedTextField(
				value = uiState.apiPass,
				onValueChange = viewModel::updateApiPass,
				label = { Text(stringResource(R.string.virtualizor_api_pass)) },
				singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				modifier = Modifier.fillMaxWidth()
			)
			Button(
				onClick = { viewModel.save() },
				enabled = !uiState.busy && uiState.host.isNotBlank() && uiState.apiKey.isNotBlank() && uiState.apiPass.isNotBlank(),
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					stringResource(
						if (uiState.account == null) R.string.virtualizor_save_account else R.string.virtualizor_replace_keys
					)
				)
			}
			if (uiState.account != null) {
				TextButton(onClick = { viewModel.requestDelete() }) {
					Text(stringResource(R.string.virtualizor_delete_account))
				}
			}
			if (uiState.instances.isEmpty() && uiState.account != null) {
				Text(stringResource(R.string.virtualizor_no_instances))
			}
			uiState.instances.forEach { row ->
				InstanceCard(
					row = row,
					onAddServer = onAddServer,
					onOpenServer = onOpenServer,
					onBandwidth = { viewModel.queryBandwidth(row.vps.id) }
				)
			}
		}
	}

	uiState.pendingPin?.let { pin ->
		AlertDialog(
			onDismissRequest = { viewModel.dismissPin() },
			title = { Text(stringResource(R.string.virtualizor_pin_title)) },
			text = { Text(stringResource(R.string.virtualizor_pin_confirm, pin)) },
			confirmButton = {
				TextButton(onClick = { viewModel.confirmPin() }) {
					Text(stringResource(R.string.confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.dismissPin() }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}

	uiState.bandwidth?.let { bandwidth ->
		AlertDialog(
			onDismissRequest = { viewModel.dismissBandwidth() },
			text = { VirtualizorBandwidthPanel(bandwidth) },
			confirmButton = {
				TextButton(onClick = { viewModel.dismissBandwidth() }) {
					Text(stringResource(R.string.confirm))
				}
			}
		)
	}

	if (uiState.confirmDelete) {
		AlertDialog(
			onDismissRequest = { viewModel.dismissDelete() },
			title = { Text(stringResource(R.string.virtualizor_delete_account)) },
			text = { Text(stringResource(R.string.virtualizor_delete_account_confirm)) },
			confirmButton = {
				TextButton(onClick = { viewModel.deleteAccount() }) {
					Text(stringResource(R.string.confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.dismissDelete() }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}
}

@Composable
private fun InstanceCard(
	row: VirtualizorInstanceRow,
	onAddServer: (String, String?) -> Unit,
	onOpenServer: (Long) -> Unit,
	onBandwidth: () -> Unit
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			val address = row.vps.address
			Text(
				if (address.isNullOrBlank()) row.vps.id else "${row.vps.id} · $address",
				style = MaterialTheme.typography.titleMedium
			)
			row.vps.hostname?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
			TextButton(onClick = onBandwidth) {
				Text(stringResource(R.string.virtualizor_bandwidth))
			}
			if (row.linkedServerId == null) {
				Text(stringResource(R.string.virtualizor_not_linked))
				TextButton(onClick = { onAddServer(row.vps.id, row.vps.address) }) {
					Text(stringResource(R.string.virtualizor_add_server))
				}
			} else {
				Text(stringResource(R.string.virtualizor_linked))
				TextButton(onClick = { onOpenServer(row.linkedServerId) }) {
					Text(stringResource(R.string.virtualizor_open_server))
				}
			}
		}
	}
}
