package com.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcserver.models.LanguageMode
import com.vcserver.models.ThemeMode
import com.vcserver.ui.viewmodels.SettingsViewModel

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

	// 显示错误提示
	LaunchedEffect(uiState.errorMessage) {
		uiState.errorMessage?.let { error ->
			snackbarHostState.showSnackbar(
				message = error,
				duration = SnackbarDuration.Short
			)
			viewModel.clearError()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("设置") },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
						text = "主题",
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
						text = "语言",
						style = MaterialTheme.typography.titleMedium
					)
					LanguageModeSelector(
						currentLanguage = uiState.settings.language,
						onLanguageSelected = { language ->
							viewModel.updateLanguage(language) {
								// 语言更改后重启 Activity
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
						text = "连接设置",
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
						label = { Text("连接超时时间（秒）") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)

					// 默认 SSH 端口
					OutlinedTextField(
						value = uiState.settings.defaultSshPort.toString(),
						onValueChange = { value ->
							value.toIntOrNull()?.let {
								viewModel.updateDefaultSshPort(it)
							}
						},
						label = { Text("默认 SSH 端口") },
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
						text = "显示设置",
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
						label = { Text("服务器监控刷新间隔（秒）") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)
				}
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
						Text(
							text = "代理设置",
							style = MaterialTheme.typography.titleMedium
						)
						Switch(
							checked = uiState.settings.proxyEnabled,
							onCheckedChange = { enabled ->
								viewModel.updateProxy(
									enabled = enabled,
									host = uiState.settings.proxyHost,
									port = uiState.settings.proxyPort,
									username = uiState.settings.proxyUsername,
									password = uiState.settings.proxyPassword
								)
							}
						)
					}

					if (uiState.settings.proxyEnabled) {
						OutlinedTextField(
							value = uiState.settings.proxyHost,
							onValueChange = { host ->
								viewModel.updateProxy(
									enabled = true,
									host = host,
									port = uiState.settings.proxyPort,
									username = uiState.settings.proxyUsername,
									password = uiState.settings.proxyPassword
								)
							},
							label = { Text("代理主机") },
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						OutlinedTextField(
							value = uiState.settings.proxyPort.toString(),
							onValueChange = { value ->
								value.toIntOrNull()?.let { port ->
									viewModel.updateProxy(
										enabled = true,
										host = uiState.settings.proxyHost,
										port = port,
										username = uiState.settings.proxyUsername,
										password = uiState.settings.proxyPassword
									)
								}
							},
							label = { Text("代理端口") },
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						OutlinedTextField(
							value = uiState.settings.proxyUsername,
							onValueChange = { username ->
								viewModel.updateProxy(
									enabled = true,
									host = uiState.settings.proxyHost,
									port = uiState.settings.proxyPort,
									username = username,
									password = uiState.settings.proxyPassword
								)
							},
							label = { Text("用户名（可选）") },
							modifier = Modifier.fillMaxWidth(),
							singleLine = true
						)

						OutlinedTextField(
							value = uiState.settings.proxyPassword,
							onValueChange = { password ->
								viewModel.updateProxy(
									enabled = true,
									host = uiState.settings.proxyHost,
									port = uiState.settings.proxyPort,
									username = uiState.settings.proxyUsername,
									password = password
								)
							},
							label = { Text("密码（可选）") },
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
						text = "关于",
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = ">",
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
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
				Text("重置为默认值")
			}
		}
	}

	// 重置确认对话框
	if (uiState.showResetDialog) {
		AlertDialog(
			onDismissRequest = { viewModel.hideResetDialog() },
			title = { Text("重置设置") },
			text = { Text("确定要将所有设置重置为默认值吗？") },
			confirmButton = {
				TextButton(onClick = { viewModel.resetToDefaults() }) {
					Text("确定")
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.hideResetDialog() }) {
					Text("取消")
				}
			}
		)
	}
}

/**
 * 主题模式选择器
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
						ThemeMode.LIGHT -> "浅色"
						ThemeMode.DARK -> "深色"
						ThemeMode.SYSTEM -> "跟随系统"
					},
					style = MaterialTheme.typography.bodyLarge
				)
			}
		}
	}
}

/**
 * 语言模式选择器
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
						LanguageMode.CHINESE -> "中文"
						LanguageMode.ENGLISH -> "English"
						LanguageMode.SYSTEM -> "跟随系统"
					},
					style = MaterialTheme.typography.bodyLarge
				)
			}
		}
	}
}

