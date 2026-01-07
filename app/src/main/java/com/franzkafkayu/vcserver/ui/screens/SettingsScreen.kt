package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.LanguageMode
import com.franzkafkayu.vcserver.models.ProxyType
import com.franzkafkayu.vcserver.models.ThemeMode
import com.franzkafkayu.vcserver.ui.viewmodels.SettingsViewModel

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	viewModel: SettingsViewModel,
	onBackClick: () -> Unit,
	onAboutClick: () -> Unit
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current

	// 导入确认对话框状态
	var showImportDialog by remember { mutableStateOf<Uri?>(null) }

	// 导出文件选择器
	val exportLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("application/json")
	) { uri: Uri? ->
		uri?.let {
			viewModel.exportDatabase(context, it)
		}
	}

	// 导入文件选择器
	val importLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uri: Uri? ->
		uri?.let {
			showImportDialog = it
		}
	}

	// 显示错误提示
	LaunchedEffect(uiState.errorMessage) {
		uiState.errorMessage?.let { error ->
			val localizedError = getLocalizedErrorMessage(context, error)
			snackbarHostState.showSnackbar(
				message = localizedError,
				duration = SnackbarDuration.Short
			)
			viewModel.clearError()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.settings)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
			verticalArrangement = Arrangement.spacedBy(24.dp)
		) {
			// 主题设置
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = stringResource(R.string.theme),
						style = MaterialTheme.typography.titleMedium
					)
					ThemeModeSelector(
						currentTheme = uiState.settings.theme,
						onThemeSelected = { viewModel.updateTheme(it) }
					)
				}
			}

			// 语言设置
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = stringResource(R.string.language),
						style = MaterialTheme.typography.titleMedium
					)
					LanguageModeSelector(
						currentLanguage = uiState.settings.language,
						onLanguageSelected = { language ->
							viewModel.updateLanguage(language) {
								// 语言更改后重�?Activity
								(context as? androidx.activity.ComponentActivity)?.recreate()
							}
						}
					)
				}
			}

			// 连接设置
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = stringResource(R.string.connection_settings),
						style = MaterialTheme.typography.titleMedium
					)
					
					// 连接超时时间
					OutlinedTextField(
						value = uiState.settings.connectionTimeout.toString(),
						onValueChange = { value ->
							value.toIntOrNull()?.let {
								viewModel.updateConnectionTimeout(it)
							}
						},
						label = { Text(stringResource(R.string.connection_timeout_seconds)) },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)
				}
			}

			// 显示设置
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = stringResource(R.string.display_settings),
						style = MaterialTheme.typography.titleMedium
					)
					
					// 刷新间隔
					OutlinedTextField(
						value = uiState.settings.refreshInterval.toString(),
						onValueChange = { value ->
							value.toIntOrNull()?.let {
								viewModel.updateRefreshInterval(it)
							}
						},
						label = { Text(stringResource(R.string.refresh_interval_seconds)) },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)
				}
			}

			// 默认代理配置（可折叠�?
			var proxyExpanded by remember { mutableStateOf(false) }
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { proxyExpanded = !proxyExpanded },
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column {
							Text(
								text = stringResource(R.string.default_proxy_config),
								style = MaterialTheme.typography.titleMedium
							)
							Text(
								text = stringResource(R.string.default_proxy_template),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}

					if (proxyExpanded) {
						Divider()

						// 代理类型选择
						Column(
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Text(
								text = stringResource(R.string.proxy_type),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							ProxyTypeSelector(
								currentType = uiState.settings.defaultProxyType,
								onTypeSelected = { type ->
									viewModel.updateDefaultProxy(
										type = type,
										host = uiState.settings.defaultProxyHost,
										port = uiState.settings.defaultProxyPort,
										username = uiState.settings.defaultProxyUsername,
										password = uiState.settings.defaultProxyPassword
									)
								}
							)
						}

					Divider()

					// 代理服务器配置
					Text(
						text = stringResource(R.string.proxy_server),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)

					OutlinedTextField(
						value = uiState.settings.defaultProxyHost,
						onValueChange = { host ->
							viewModel.updateDefaultProxy(
								type = uiState.settings.defaultProxyType,
								host = host,
								port = uiState.settings.defaultProxyPort,
								username = uiState.settings.defaultProxyUsername,
								password = uiState.settings.defaultProxyPassword
							)
						},
						label = { Text(stringResource(R.string.proxy_host)) },
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)

						OutlinedTextField(
							value = uiState.settings.defaultProxyPort.toString(),
							onValueChange = { value ->
								value.toIntOrNull()?.let { port ->
									viewModel.updateDefaultProxy(
										type = uiState.settings.defaultProxyType,
										host = uiState.settings.defaultProxyHost,
										port = port,
										username = uiState.settings.defaultProxyUsername,
										password = uiState.settings.defaultProxyPassword
									)
								}
							},
							label = { Text(stringResource(R.string.proxy_port)) },
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						Divider()

						// 代理鉴权配置
						Text(
							text = stringResource(R.string.proxy_auth_optional),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)

						OutlinedTextField(
							value = uiState.settings.defaultProxyUsername,
							onValueChange = { username ->
								viewModel.updateDefaultProxy(
									type = uiState.settings.defaultProxyType,
									host = uiState.settings.defaultProxyHost,
									port = uiState.settings.defaultProxyPort,
									username = username,
									password = uiState.settings.defaultProxyPassword
								)
							},
							label = { Text(stringResource(R.string.username)) },
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						OutlinedTextField(
							value = uiState.settings.defaultProxyPassword,
							onValueChange = { password ->
								viewModel.updateDefaultProxy(
									type = uiState.settings.defaultProxyType,
									host = uiState.settings.defaultProxyHost,
									port = uiState.settings.defaultProxyPort,
									username = uiState.settings.defaultProxyUsername,
									password = password
								)
							},
							label = { Text(stringResource(R.string.password)) },
							visualTransformation = PasswordVisualTransformation(),
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)
					}
				}
			}

			// 关于按钮
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.clickable(onClick = onAboutClick)
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = stringResource(R.string.about),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = ">",
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			// 数据库导出/导入
			Card {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = stringResource(R.string.database_export_import),
						style = MaterialTheme.typography.titleMedium
					)
					
					// 导出按钮
					Button(
						onClick = {
							exportLauncher.launch("vcserver_backup_${System.currentTimeMillis()}.json")
						},
						modifier = Modifier.fillMaxWidth(),
						enabled = !uiState.isExporting
					) {
						if (uiState.isExporting) {
							CircularProgressIndicator(
								modifier = Modifier.size(20.dp),
								color = MaterialTheme.colorScheme.onPrimary
							)
							Spacer(modifier = Modifier.width(8.dp))
						}
						Text(stringResource(R.string.export_database))
					}

					// 导入按钮
					Button(
						onClick = {
							importLauncher.launch("application/json")
						},
						modifier = Modifier.fillMaxWidth(),
						enabled = !uiState.isImporting,
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.secondary
						)
					) {
						if (uiState.isImporting) {
							CircularProgressIndicator(
								modifier = Modifier.size(20.dp),
								color = MaterialTheme.colorScheme.onSecondary
							)
							Spacer(modifier = Modifier.width(8.dp))
						}
						Text(stringResource(R.string.import_database))
					}
				}
			}

			// 重置按钮
			Button(
				onClick = { viewModel.showResetDialog() },
				modifier = Modifier.fillMaxWidth(),
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.error
				)
			) {
				Text(stringResource(R.string.reset_to_defaults))
			}
		}
	}

	// 显示导出/导入结果
	LaunchedEffect(uiState.exportSuccess) {
		if (uiState.exportSuccess) {
			snackbarHostState.showSnackbar(
				message = context.getString(R.string.export_success),
				duration = SnackbarDuration.Short
			)
			viewModel.clearExportSuccess()
		}
	}

	LaunchedEffect(uiState.importSuccess) {
		uiState.importSuccess?.let { count ->
			snackbarHostState.showSnackbar(
				message = context.getString(R.string.import_success, count),
				duration = SnackbarDuration.Short
			)
			viewModel.clearImportSuccess()
		}
	}

	// 重置确认对话框
	if (uiState.showResetDialog) {
		AlertDialog(
			onDismissRequest = { viewModel.hideResetDialog() },
			title = { Text(stringResource(R.string.reset_settings)) },
			text = { Text(stringResource(R.string.reset_settings_confirm)) },
			confirmButton = {
				TextButton(onClick = { viewModel.resetToDefaults() }) {
					Text(stringResource(R.string.confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.hideResetDialog() }) {
					Text(stringResource(R.string.cancel))
				}
			}
		)
	}

	// 导入确认对话框
	showImportDialog?.let { uri ->
		AlertDialog(
			onDismissRequest = { showImportDialog = null },
			title = { Text(stringResource(R.string.import_database)) },
			text = { Text(stringResource(R.string.import_database_confirm)) },
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.importDatabase(context, uri, importStrategy = true)
						showImportDialog = null
					}
				) {
					Text(stringResource(R.string.overwrite))
				}
			},
			dismissButton = {
				Row {
					TextButton(
						onClick = {
							viewModel.importDatabase(context, uri, importStrategy = false)
							showImportDialog = null
						}
					) {
						Text(stringResource(R.string.skip))
					}
					TextButton(onClick = { showImportDialog = null }) {
						Text(stringResource(R.string.cancel))
					}
				}
			}
		)
	}
}

/**
 * 主题模式选择�?
 */
@Composable
private fun ThemeModeSelector(
	currentTheme: ThemeMode,
	onThemeSelected: (ThemeMode) -> Unit
) {
	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		ThemeMode.values().forEach { theme ->
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onThemeSelected(theme) },
				verticalAlignment = Alignment.CenterVertically
			) {
				RadioButton(
					selected = currentTheme == theme,
					onClick = { onThemeSelected(theme) }
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = when (theme) {
						ThemeMode.LIGHT -> stringResource(R.string.theme_light)
						ThemeMode.DARK -> stringResource(R.string.theme_dark)
						ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
					},
					style = MaterialTheme.typography.bodyLarge
				)
			}
		}
	}
}

/**
 * 语言模式选择�?
 */
@Composable
private fun LanguageModeSelector(
	currentLanguage: LanguageMode,
	onLanguageSelected: (LanguageMode) -> Unit
) {
	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		LanguageMode.values().forEach { language ->
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onLanguageSelected(language) },
				verticalAlignment = Alignment.CenterVertically
			) {
				RadioButton(
					selected = currentLanguage == language,
					onClick = { onLanguageSelected(language) }
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = when (language) {
						LanguageMode.CHINESE -> stringResource(R.string.language_chinese)
						LanguageMode.ENGLISH -> stringResource(R.string.language_english)
						LanguageMode.SYSTEM -> stringResource(R.string.language_system)
					},
					style = MaterialTheme.typography.bodyLarge
				)
			}
		}
	}
}

/**
 * 代理类型选择�?
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyTypeSelector(
	currentType: ProxyType,
	onTypeSelected: (ProxyType) -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		ProxyType.values().forEach { type ->
			FilterChip(
				selected = currentType == type,
				onClick = { onTypeSelected(type) },
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

/**
 * 将错误键值转换为本地化错误消息
 */
private fun getLocalizedErrorMessage(context: Context, errorKey: String): String {
	// 错误消息格式: "OPERATION_FAILED:ERROR_KEY"
	val parts = errorKey.split(":", limit = 2)
	if (parts.size != 2) {
		return errorKey // 如果不是预期格式，直接返回原消息
	}
	
	val operation = parts[0]
	val errorCode = parts[1]
	
	// 根据操作类型和错误代码返回本地化字符串
	return when (operation) {
		"UPDATE_THEME_FAILED" -> {
			context.getString(R.string.error_update_theme_failed, getErrorDetailMessage(context, errorCode))
		}
		"UPDATE_LANGUAGE_FAILED" -> {
			context.getString(R.string.error_update_language_failed, getErrorDetailMessage(context, errorCode))
		}
		"UPDATE_CONNECTION_TIMEOUT_FAILED" -> {
			context.getString(R.string.error_update_connection_timeout_failed, getErrorDetailMessage(context, errorCode))
		}
		"UPDATE_DEFAULT_PORT_FAILED" -> {
			context.getString(R.string.error_update_default_port_failed, getErrorDetailMessage(context, errorCode))
		}
		"UPDATE_REFRESH_INTERVAL_FAILED" -> {
			context.getString(R.string.error_update_refresh_interval_failed, getErrorDetailMessage(context, errorCode))
		}
		"UPDATE_DEFAULT_PROXY_FAILED" -> {
			context.getString(R.string.error_update_default_proxy_failed, getErrorDetailMessage(context, errorCode))
		}
		"RESET_SETTINGS_FAILED" -> {
			context.getString(R.string.error_reset_settings_failed, getErrorDetailMessage(context, errorCode))
		}
		"EXPORT_SUCCESS" -> {
			context.getString(R.string.export_success)
		}
		"EXPORT_FAILED" -> {
			context.getString(R.string.error_export_failed, getErrorDetailMessage(context, errorCode))
		}
		"IMPORT_SUCCESS" -> {
			val count = errorCode.toIntOrNull() ?: 0
			context.getString(R.string.import_success, count)
		}
		"IMPORT_FAILED" -> {
			context.getString(R.string.error_import_failed, getErrorDetailMessage(context, errorCode))
		}
		else -> errorKey
	}
}

/**
 * 获取错误详情消息
 */
private fun getErrorDetailMessage(context: Context, errorCode: String): String {
	return when (errorCode) {
		"CONNECTION_TIMEOUT_INVALID" -> context.getString(R.string.error_connection_timeout_invalid)
		"SSH_PORT_INVALID" -> context.getString(R.string.error_ssh_port_invalid)
		"REFRESH_INTERVAL_INVALID" -> context.getString(R.string.error_refresh_interval_invalid)
		"PROXY_PORT_INVALID" -> context.getString(R.string.error_proxy_port_invalid)
		"EXPORT_NO_SERVERS" -> context.getString(R.string.error_export_no_servers)
		"EXPORT_OPEN_STREAM_FAILED" -> context.getString(R.string.error_export_open_stream_failed)
		"IMPORT_OPEN_STREAM_FAILED" -> context.getString(R.string.error_import_open_stream_failed)
		"IMPORT_INVALID_FORMAT" -> context.getString(R.string.error_import_invalid_format)
		else -> errorCode
	}
}

