package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.ProxyType
import com.franzkafkayu.vcserver.ui.viewmodels.AddServerViewModel

/**
 * 添加服务器界�?
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
	viewModel: AddServerViewModel,
	onBackClick: () -> Unit,
	onSaveSuccess: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

	// 进入界面时，只在新增模式下重置状态（编辑模式下由 ViewModel �?init 加载数据�?
	LaunchedEffect(Unit) {
		if (!uiState.isEditMode) {
			viewModel.reset()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { 
					Text(
						if (uiState.isEditMode) stringResource(R.string.edit_server) 
						else stringResource(R.string.add_server)
					) 
				},
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// 服务器名�?
			OutlinedTextField(
				value = uiState.name,
				onValueChange = viewModel::updateName,
				label = { Text(stringResource(R.string.server_name)) },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true
			)

			// 主机地址
			OutlinedTextField(
				value = uiState.host,
				onValueChange = viewModel::updateHost,
				label = { Text(stringResource(R.string.host)) },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true
			)

			// 端口
			OutlinedTextField(
				value = uiState.port,
				onValueChange = viewModel::updatePort,
				label = { Text(stringResource(R.string.port)) },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
			)

			// 用户�?
			OutlinedTextField(
				value = uiState.username,
				onValueChange = viewModel::updateUsername,
				label = { Text(stringResource(R.string.username)) },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true
			)

			// 认证方式
			Text(
				text = stringResource(R.string.auth_type),
				style = MaterialTheme.typography.labelLarge
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				FilterChip(
					selected = uiState.authType == AuthType.PASSWORD,
					onClick = { viewModel.updateAuthType(AuthType.PASSWORD) },
					label = { Text(stringResource(R.string.auth_password)) }
				)
				FilterChip(
					selected = uiState.authType == AuthType.KEY,
					onClick = { viewModel.updateAuthType(AuthType.KEY) },
					label = { Text(stringResource(R.string.auth_key)) }
				)
			}

			// 密码输入（当认证方式为密码时显示�?
			if (uiState.authType == AuthType.PASSWORD) {
				OutlinedTextField(
					value = uiState.password,
					onValueChange = viewModel::updatePassword,
					label = { Text(stringResource(R.string.password)) },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
					trailingIcon = {
						IconButton(onClick = viewModel::togglePasswordVisibility) {
							Icon(
								imageVector = if (uiState.passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
								contentDescription = if (uiState.passwordVisible) "隐藏密码" else "显示密码"
							)
						}
					}
				)
			}

			// 私钥输入（当认证方式为密钥时显示�?
			if (uiState.authType == AuthType.KEY) {
				OutlinedTextField(
					value = uiState.privateKey,
					onValueChange = viewModel::updatePrivateKey,
					label = { Text(stringResource(R.string.private_key)) },
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 120.dp),
					minLines = 5
				)

				// 密钥密码（可选）
				OutlinedTextField(
					value = uiState.keyPassphrase,
					onValueChange = viewModel::updateKeyPassphrase,
					label = { Text(stringResource(R.string.key_passphrase)) },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					visualTransformation = if (uiState.keyPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
					trailingIcon = {
						IconButton(onClick = viewModel::toggleKeyPassphraseVisibility) {
							Icon(
								imageVector = if (uiState.keyPassphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
								contentDescription = if (uiState.keyPassphraseVisible) "隐藏密钥密码" else "显示密钥密码"
							)
						}
					}
				)
			}

			// 代理设置
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column {
							Text(
								text = "代理设置",
								style = MaterialTheme.typography.titleMedium
							)
							Text(
								text = "为当前服务器启用代理连接",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = uiState.proxyEnabled,
							onCheckedChange = { enabled ->
								viewModel.updateProxyEnabled(enabled)
							}
						)
					}

					if (uiState.proxyEnabled) {
						Divider()

						// 代理类型选择
						Column(
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Text(
								text = "代理类型",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								ProxyType.values().forEach { type ->
									FilterChip(
										selected = uiState.proxyType == type,
										onClick = { viewModel.updateProxyType(type) },
										label = {
											Text(
												text = when (type) {
													ProxyType.HTTP -> "HTTP"
													ProxyType.SOCKS5 -> "SOCKS5"
												}
											)
										},
										modifier = Modifier.weight(1f)
									)
								}
							}
						}

					Divider()

					// 代理服务器配置
					Text(
						text = "代理服务器",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)

					OutlinedTextField(
						value = uiState.proxyHost,
						onValueChange = viewModel::updateProxyHost,
						label = { Text("代理主机") },
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)

						OutlinedTextField(
							value = uiState.proxyPort,
							onValueChange = viewModel::updateProxyPort,
							label = { Text("代理端口") },
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						Divider()

						// 代理鉴权配置
						Text(
							text = "代理鉴权（可选）",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)

						OutlinedTextField(
							value = uiState.proxyUsername,
							onValueChange = viewModel::updateProxyUsername,
							label = { Text("用户名") },
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						OutlinedTextField(
							value = uiState.proxyPassword,
							onValueChange = viewModel::updateProxyPassword,
							label = { Text("密码") },
							visualTransformation = if (uiState.proxyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
							modifier = Modifier.fillMaxWidth(),
							singleLine = true,
							trailingIcon = {
								IconButton(onClick = viewModel::toggleProxyPasswordVisibility) {
									Icon(
										imageVector = if (uiState.proxyPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
										contentDescription = if (uiState.proxyPasswordVisible) "隐藏代理密码" else "显示代理密码"
									)
								}
							}
						)
					}
				}
			}

			// 连接测试成功提示
			if (uiState.connectionTestSuccess) {
				Text(
					text = stringResource(R.string.connection_success),
					color = MaterialTheme.colorScheme.primary,
					style = MaterialTheme.typography.bodyMedium
				)
			}

			// 错误提示
			uiState.error?.let { error ->
				Text(
					text = error.message,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodyMedium
				)
			}

			// 按钮
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Button(
					onClick = viewModel::testConnection,
					enabled = !uiState.isTestingConnection && !uiState.isSaving,
					modifier = Modifier.weight(1f)
				) {
					if (uiState.isTestingConnection) {
						CircularProgressIndicator(
							modifier = Modifier.size(16.dp),
							color = MaterialTheme.colorScheme.onPrimary
						)
					} else {
						Text(stringResource(R.string.test_connection))
					}
				}
				Button(
					onClick = { viewModel.saveServer(onSaveSuccess) },
					enabled = !uiState.isSaving && !uiState.isTestingConnection,
					modifier = Modifier.weight(1f)
				) {
					if (uiState.isSaving) {
						CircularProgressIndicator(
							modifier = Modifier.size(16.dp),
							color = MaterialTheme.colorScheme.onPrimary
						)
					} else {
						Text(stringResource(R.string.save))
					}
				}
			}
		}
	}

	// 保存成功后导航返�?
	LaunchedEffect(uiState.saveSuccess) {
		if (uiState.saveSuccess) {
			onSaveSuccess()
		}
	}
}

