package com.franzkafkayu.vcserver.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.DiskInfo
import com.franzkafkayu.vcserver.models.NetworkInfo
import com.franzkafkayu.vcserver.models.NetworkInterfaceInfo
import com.franzkafkayu.vcserver.ui.viewmodels.ServerMonitoringViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * 服务器监控界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerMonitoringScreen(
	viewModel: ServerMonitoringViewModel,
	onBackClick: () -> Unit,
	onEnterTerminal: () -> Unit = {}
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	val context = LocalContext.current
	val snackbarHostState = remember { SnackbarHostState() }

	// 文件传输对话框状态
	var showFileTransferDialog by remember { mutableStateOf<FileTransferDialogType?>(null) }
	var remotePath by remember { mutableStateOf("") }
	var selectedLocalUri by remember { mutableStateOf<Uri?>(null) }

	// 本地文件选择器（用于上传）
	val uploadFileLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uri: Uri? ->
		uri?.let {
			selectedLocalUri = it
			showFileTransferDialog = FileTransferDialogType.Upload
		}
	}

	// 本地保存位置选择器（用于下载）
	val downloadFileLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("*/*")
	) { uri: Uri? ->
		uri?.let {
			if (remotePath.isNotBlank()) {
				viewModel.downloadFile(context, remotePath, it)
				showFileTransferDialog = null
				remotePath = ""
			}
		}
	}

	// 文件传输成功后，延迟清除成功状态（显示成功提示 2 秒后自动消失）
	LaunchedEffect(uiState.fileTransferSuccess) {
		if (uiState.fileTransferSuccess) {
			// 2 秒后自动清除成功状态
			kotlinx.coroutines.delay(2000)
			viewModel.clearFileTransferSuccess()
		}
	}

	LaunchedEffect(uiState.fileTransferError) {
		uiState.fileTransferError?.let { errorKey ->
			val message = getSftpErrorMessage(context, errorKey)
			snackbarHostState.showSnackbar(message)
			viewModel.clearFileTransferError()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(viewModel.server.name) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
					}
				},
				actions = {
					IconButton(
						onClick = { showFileTransferDialog = FileTransferDialogType.Menu },
						enabled = !uiState.isLoading && !uiState.isFileTransferring
					) {
						Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.sftp_file_transfer))
					}
					IconButton(
						onClick = onEnterTerminal,
						enabled = !uiState.isLoading && !uiState.isFileTransferring
					) {
						Icon(Icons.Default.Code, contentDescription = stringResource(R.string.enter_terminal))
					}
					IconButton(
						onClick = { viewModel.loadServerStatus() },
						enabled = !uiState.isLoading && !uiState.isFileTransferring
					) {
						if (uiState.isLoading) {
							CircularProgressIndicator(
								modifier = Modifier.size(24.dp),
								strokeWidth = 2.dp
							)
						} else {
							Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
						}
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
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				// SFTP 文件传输进度条或成功提示
				if (uiState.isFileTransferring || uiState.fileTransferSuccess) {
					Card(
						modifier = Modifier.fillMaxWidth(),
						colors = CardDefaults.cardColors(
							containerColor = if (uiState.fileTransferSuccess) {
								MaterialTheme.colorScheme.tertiaryContainer
							} else {
								MaterialTheme.colorScheme.primaryContainer
							}
						)
					) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							if (uiState.fileTransferSuccess) {
								// 显示成功消息
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.Center,
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										imageVector = Icons.Default.CheckCircle,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.tertiary,
										modifier = Modifier.size(20.dp)
									)
									Spacer(modifier = Modifier.width(8.dp))
									Text(
										text = stringResource(R.string.sftp_operation_success),
										style = MaterialTheme.typography.titleSmall,
										color = MaterialTheme.colorScheme.onTertiaryContainer,
										fontWeight = FontWeight.Medium
									)
								}
							} else {
								// 显示传输进度
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Text(
										text = when (uiState.fileTransferType) {
											"upload" -> stringResource(R.string.sftp_uploading)
											"download" -> stringResource(R.string.sftp_downloading)
											else -> stringResource(R.string.sftp_file_transfer)
										},
										style = MaterialTheme.typography.titleSmall,
										color = MaterialTheme.colorScheme.onPrimaryContainer
									)
									Text(
										text = "${(uiState.fileTransferProgress * 100).toInt()}%",
										style = MaterialTheme.typography.titleSmall,
										color = MaterialTheme.colorScheme.onPrimaryContainer,
										fontWeight = FontWeight.Bold
									)
								}
								LinearProgressIndicator(
									progress = uiState.fileTransferProgress,
									modifier = Modifier.fillMaxWidth(),
									color = MaterialTheme.colorScheme.primary,
									trackColor = MaterialTheme.colorScheme.surfaceVariant
								)
								if (uiState.fileTransferTotal > 0) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(
											text = formatBytes(uiState.fileTransferTransferred),
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onPrimaryContainer
										)
										Text(
											text = formatBytes(uiState.fileTransferTotal),
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onPrimaryContainer
										)
									}
								}
							}
						}
					}
				}

				// 错误提示
				uiState.error?.let { error ->
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.errorContainer
					)
				) {
					Text(
						text = error.message,
						modifier = Modifier.padding(16.dp),
						color = MaterialTheme.colorScheme.onErrorContainer
					)
				}
			}

			// 加载状态
			if (uiState.isLoading && uiState.serverStatus == null) {
				Box(
					modifier = Modifier.fillMaxWidth(),
					contentAlignment = androidx.compose.ui.Alignment.Center
				) {
					CircularProgressIndicator()
				}
			}

			// 服务器状态信息
			uiState.serverStatus?.let { status ->
				// 系统信息（如果有�?
				status.systemInfo?.let { systemInfo ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Text(
								text = stringResource(R.string.system_info),
								style = MaterialTheme.typography.titleMedium
							)
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(R.string.os_name))
								Text("${systemInfo.osName} ${systemInfo.osVersion}", fontWeight = FontWeight.Medium)
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(R.string.kernel_version))
								Text(systemInfo.kernelVersion, fontWeight = FontWeight.Medium)
							}
						}
					}
				}

				// CPU 信息
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(
						modifier = Modifier.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Text(
							text = stringResource(R.string.cpu_info),
							style = MaterialTheme.typography.titleMedium
						)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							// 左侧：CPU 信息
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(8.dp)
							) {
								// CPU 架构
								status.cpu.architecture?.let { arch ->
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(stringResource(R.string.cpu_architecture))
										Text(arch, fontWeight = FontWeight.Medium)
									}
								}
								
								// CPU 型号
								status.cpu.model?.let { model ->
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(stringResource(R.string.cpu_model))
										Text(
											text = model,
											fontWeight = FontWeight.Medium,
											modifier = Modifier.weight(1f),
											style = MaterialTheme.typography.bodyMedium
										)
									}
								}
								
								// CPU 核心
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(R.string.cpu_cores))
									Text("${status.cpu.cores}", fontWeight = FontWeight.Medium)
								}
							}
							
							// 右侧：圆形进度图
							Box(
								modifier = Modifier.size(100.dp),
								contentAlignment = Alignment.Center
							) {
								CircularProgress(
									progress = (status.cpu.usagePercent / 100.0).toFloat(),
									modifier = Modifier.fillMaxSize()
								)
								Column(
									horizontalAlignment = Alignment.CenterHorizontally
								) {
									Text(
										text = "${String.format("%.1f", status.cpu.usagePercent)}%",
										style = MaterialTheme.typography.titleMedium,
										fontWeight = FontWeight.Bold
									)
									Text(
										text = stringResource(R.string.cpu_usage),
										style = MaterialTheme.typography.bodySmall
									)
								}
							}
						}
					}
				}

				// 内存信息
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(
						modifier = Modifier.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Text(
							text = stringResource(R.string.memory_info),
							style = MaterialTheme.typography.titleMedium
						)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							// 左侧：内存信息
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(8.dp)
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(R.string.total_memory))
									Text(status.memory.total, fontWeight = FontWeight.Medium)
								}
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(R.string.used_memory))
									Text(status.memory.used, fontWeight = FontWeight.Medium)
								}
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(R.string.available_memory))
									Text(status.memory.available, fontWeight = FontWeight.Medium)
								}
							}
							
							// 右侧：圆形进度图
							Box(
								modifier = Modifier.size(100.dp),
								contentAlignment = Alignment.Center
							) {
								CircularProgress(
									progress = (status.memory.usagePercent / 100.0).toFloat(),
									modifier = Modifier.fillMaxSize()
								)
								Column(
									horizontalAlignment = Alignment.CenterHorizontally
								) {
									Text(
										text = "${String.format("%.1f", status.memory.usagePercent)}%",
										style = MaterialTheme.typography.titleMedium,
										fontWeight = FontWeight.Bold
									)
									Text(
										text = stringResource(R.string.memory_usage),
										style = MaterialTheme.typography.bodySmall
									)
								}
							}
						}
					}
				}

				// 磁盘信息
				status.disk?.let { disk ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(16.dp)
						) {
							Text(
								text = stringResource(R.string.disk_info),
								style = MaterialTheme.typography.titleMedium
							)
							DiskInfoItem(disk = disk)
						}
					}
				}

				// 网络信息（如果有）
				status.network?.let { network ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(16.dp)
						) {
							Text(
								text = "网络信息",
								style = MaterialTheme.typography.titleMedium
							)
							
							// 连接数信息
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text("TCP 连接数")
								Text("${network.tcpConnections}", fontWeight = FontWeight.Medium)
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text("UDP 连接数")
								Text("${network.udpConnections}", fontWeight = FontWeight.Medium)
							}
							
							// 网卡列表
							if (network.interfaces.isNotEmpty()) {
								Divider()
								Text(
									text = "网卡信息 (${network.interfaces.size})",
									style = MaterialTheme.typography.titleSmall
								)
								network.interfaces.forEach { interfaceInfo ->
									NetworkInterfaceItem(interfaceInfo = interfaceInfo)
								}
							}
						}
					}
				}

				// 系统启动时长（如果有）
				status.uptime?.let { uptime ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(16.dp),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Text(stringResource(R.string.uptime))
							Text(uptime)
						}
					}
				}
			}

			// 断开连接按钮
			Button(
				onClick = {
					viewModel.disconnect()
					onBackClick()
				},
				modifier = Modifier.fillMaxWidth(),
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.error
				)
			) {
				Text(stringResource(R.string.disconnect))
			}
		}
	}

	// 文件传输菜单对话框
	if (showFileTransferDialog == FileTransferDialogType.Menu) {
		AlertDialog(
			onDismissRequest = { showFileTransferDialog = null },
			title = { 
				Text(
					text = stringResource(R.string.sftp_file_transfer),
					style = MaterialTheme.typography.titleMedium,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					TextButton(
						onClick = {
							showFileTransferDialog = FileTransferDialogType.Upload
							uploadFileLauncher.launch("*/*")
						},
						modifier = Modifier.fillMaxWidth()
					) {
						Text(
							text = stringResource(R.string.sftp_upload),
							style = MaterialTheme.typography.bodyLarge
						)
					}
					TextButton(
						onClick = {
							showFileTransferDialog = FileTransferDialogType.Download
						},
						modifier = Modifier.fillMaxWidth()
					) {
						Text(
							text = stringResource(R.string.sftp_download),
							style = MaterialTheme.typography.bodyLarge
						)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showFileTransferDialog = null }) {
					Text(
						text = stringResource(R.string.cancel),
						style = MaterialTheme.typography.bodyLarge
					)
				}
			}
		)
	}

	// 上传文件对话框
	if (showFileTransferDialog == FileTransferDialogType.Upload && selectedLocalUri != null) {
		UploadFileDialog(
			remotePath = remotePath,
			onRemotePathChange = { remotePath = it },
			onConfirm = {
				selectedLocalUri?.let { uri ->
					viewModel.uploadFile(context, uri, remotePath)
					showFileTransferDialog = null
					selectedLocalUri = null
					remotePath = ""
				}
			},
			onDismiss = {
				showFileTransferDialog = null
				selectedLocalUri = null
				remotePath = ""
			},
			isTransferring = uiState.isFileTransferring,
			progress = uiState.fileTransferProgress,
			transferred = uiState.fileTransferTransferred,
			total = uiState.fileTransferTotal
		)
	}

	// 下载文件对话框
	if (showFileTransferDialog == FileTransferDialogType.Download) {
		DownloadFileDialog(
			remotePath = remotePath,
			onRemotePathChange = { remotePath = it },
			onConfirm = {
				if (remotePath.isNotBlank()) {
					// 触发文件保存位置选择器
					downloadFileLauncher.launch("")
				}
			},
			onDismiss = {
				showFileTransferDialog = null
				remotePath = ""
			},
			isTransferring = uiState.isFileTransferring,
			progress = uiState.fileTransferProgress,
			transferred = uiState.fileTransferTransferred,
			total = uiState.fileTransferTotal
		)
	}
}

/**
 * 文件传输对话框类型
 */
private enum class FileTransferDialogType {
	Menu,
	Upload,
	Download
}

/**
 * 上传文件对话框
 */
@Composable
private fun UploadFileDialog(
	remotePath: String,
	onRemotePathChange: (String) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
	isTransferring: Boolean,
	progress: Float,
	transferred: Long,
	total: Long
) {
	if (isTransferring && total >= 10 * 1024 * 1024) {
		// 大文件显示进度对话框
		AlertDialog(
			onDismissRequest = { },
			title = { 
				Text(
					text = stringResource(R.string.sftp_uploading),
					style = MaterialTheme.typography.titleLarge,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					LinearProgressIndicator(
						progress = progress,
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = "${(progress * 100).toInt()}% - ${formatBytes(transferred)} / ${formatBytes(total)}",
						style = MaterialTheme.typography.bodySmall
					)
				}
			},
			confirmButton = {}
		)
	} else {
		AlertDialog(
			onDismissRequest = onDismiss,
			title = { 
				Text(
					text = stringResource(R.string.sftp_upload_title),
					style = MaterialTheme.typography.titleLarge,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					OutlinedTextField(
						value = remotePath,
						onValueChange = onRemotePathChange,
						label = { Text(stringResource(R.string.sftp_remote_path)) },
						placeholder = { Text(stringResource(R.string.sftp_remote_path_hint)) },
						modifier = Modifier.fillMaxWidth(),
						enabled = !isTransferring,
						singleLine = true
					)
					if (isTransferring) {
						LinearProgressIndicator(
							modifier = Modifier.fillMaxWidth()
						)
						Text(
							text = stringResource(R.string.sftp_uploading),
							style = MaterialTheme.typography.bodySmall
						)
					}
				}
			},
			confirmButton = {
				TextButton(
					onClick = onConfirm,
					enabled = remotePath.isNotBlank() && !isTransferring
				) {
					Text(
						text = stringResource(R.string.confirm),
						style = MaterialTheme.typography.bodyLarge
					)
				}
			},
			dismissButton = {
				TextButton(
					onClick = onDismiss,
					enabled = !isTransferring
				) {
					Text(
						text = stringResource(R.string.cancel),
						style = MaterialTheme.typography.bodyLarge
					)
				}
			}
		)
	}
}

/**
 * 下载文件对话框
 */
@Composable
private fun DownloadFileDialog(
	remotePath: String,
	onRemotePathChange: (String) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
	isTransferring: Boolean,
	progress: Float,
	transferred: Long,
	total: Long
) {
	if (isTransferring && total >= 10 * 1024 * 1024) {
		// 大文件显示进度对话框
		AlertDialog(
			onDismissRequest = { },
			title = { 
				Text(
					text = stringResource(R.string.sftp_downloading),
					style = MaterialTheme.typography.titleLarge,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					LinearProgressIndicator(
						progress = progress,
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = "${(progress * 100).toInt()}% - ${formatBytes(transferred)} / ${formatBytes(total)}",
						style = MaterialTheme.typography.bodySmall
					)
				}
			},
			confirmButton = {}
		)
	} else {
		AlertDialog(
			onDismissRequest = onDismiss,
			title = { 
				Text(
					text = stringResource(R.string.sftp_download_title),
					style = MaterialTheme.typography.titleLarge,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					OutlinedTextField(
						value = remotePath,
						onValueChange = onRemotePathChange,
						label = { Text(stringResource(R.string.sftp_remote_path)) },
						placeholder = { Text(stringResource(R.string.sftp_remote_path_hint)) },
						modifier = Modifier.fillMaxWidth(),
						enabled = !isTransferring,
						singleLine = true
					)
					if (isTransferring) {
						LinearProgressIndicator(
							modifier = Modifier.fillMaxWidth()
						)
						Text(
							text = stringResource(R.string.sftp_downloading),
							style = MaterialTheme.typography.bodySmall
						)
					}
				}
			},
			confirmButton = {
				TextButton(
					onClick = onConfirm,
					enabled = remotePath.isNotBlank() && !isTransferring
				) {
					Text(
						text = stringResource(R.string.confirm),
						style = MaterialTheme.typography.bodyLarge
					)
				}
			},
			dismissButton = {
				TextButton(
					onClick = onDismiss,
					enabled = !isTransferring
				) {
					Text(
						text = stringResource(R.string.cancel),
						style = MaterialTheme.typography.bodyLarge
					)
				}
			}
		)
	}
}

/**
 * 格式化字节数为可读格式
 */
private fun formatBytes(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
	if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
	return "${bytes / (1024 * 1024 * 1024)} GB"
}

/**
 * 获取 SFTP 错误消息
 */
private fun getSftpErrorMessage(context: Context, errorKey: String): String {
	// 错误格式: "ERROR_KEY:详细错误信息"
	val parts = errorKey.split(":", limit = 2)
	val key = parts[0]
	val detail = if (parts.size > 1) parts[1] else ""
	
	val baseMessage = when (key) {
		"SESSION_NOT_CONNECTED" -> context.getString(R.string.error_sftp_session_not_connected)
		"UNABLE_TO_CREATE_SFTP_CHANNEL" -> context.getString(R.string.error_sftp_unable_to_create_channel)
		"UNABLE_TO_OPEN_LOCAL_FILE" -> context.getString(R.string.error_sftp_unable_to_open_local_file)
		"PERMISSION_DENIED" -> context.getString(R.string.error_sftp_permission_denied)
		"PARENT_DIR_NOT_EXISTS" -> context.getString(R.string.error_sftp_parent_dir_not_exists)
		"FILE_NOT_EXISTS" -> context.getString(R.string.error_sftp_file_not_exists)
		"REMOTE_PATH_IS_DIRECTORY" -> context.getString(R.string.error_sftp_remote_path_is_directory)
		"UPLOAD_FAILED" -> context.getString(R.string.error_sftp_upload_failed, detail.ifEmpty { key })
		"DOWNLOAD_FAILED" -> context.getString(R.string.error_sftp_download_failed, detail.ifEmpty { key })
		"SFTP_SERVICE_NOT_AVAILABLE" -> context.getString(R.string.error_sftp_service_not_available)
		else -> key
	}
	
	// 如果有详细错误信息，追加到基础消息后面
	return if (detail.isNotEmpty() && key != "UPLOAD_FAILED" && key != "DOWNLOAD_FAILED") {
		"$baseMessage\n$detail"
	} else {
		baseMessage
	}
}

/**
 * 磁盘信息项，使用圆形图表展示
 */
@Composable
fun DiskInfoItem(disk: DiskInfo) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		// 左侧：磁盘信息
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(stringResource(R.string.total_space))
				Text(disk.total, fontWeight = FontWeight.Medium)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(stringResource(R.string.used_space))
				Text(disk.used, fontWeight = FontWeight.Medium)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(stringResource(R.string.available_space))
				Text(disk.available, fontWeight = FontWeight.Medium)
			}
		}
		
		// 右侧：圆形进度图
		Box(
			modifier = Modifier.size(100.dp),
			contentAlignment = Alignment.Center
		) {
			CircularDiskProgress(
				progress = (disk.usagePercent / 100.0).toFloat(),
				modifier = Modifier.fillMaxSize()
			)
			Column(
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					text = "${String.format("%.1f", disk.usagePercent)}%",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				Text(
					text = stringResource(R.string.disk_usage),
					style = MaterialTheme.typography.bodySmall
				)
			}
		}
	}
}

/**
 * 网卡信息项
 */
@Composable
fun NetworkInterfaceItem(interfaceInfo: NetworkInterfaceInfo) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		)
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// 网卡名称
			Text(
				text = interfaceInfo.name,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold
			)
			
			// 总流量
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text("总接收", style = MaterialTheme.typography.bodySmall)
				Text(
					formatBytes(interfaceInfo.totalRxBytes),
					style = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.Medium
				)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text("总发送", style = MaterialTheme.typography.bodySmall)
				Text(
					formatBytes(interfaceInfo.totalTxBytes),
					style = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.Medium
				)
			}
			
			// 实时速率
			Divider()
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text("下行速率", style = MaterialTheme.typography.bodySmall)
				Text(
					formatRate(interfaceInfo.currentRxRate),
					style = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.primary
				)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text("上行速率", style = MaterialTheme.typography.bodySmall)
				Text(
					formatRate(interfaceInfo.currentTxRate),
					style = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.primary
				)
			}
		}
	}
}

/**
 * 格式化速率（字节/秒 -> MB/s, KB/s, B/s）
 */
private fun formatRate(bytesPerSecond: Double): String {
	return when {
		bytesPerSecond >= 1024 * 1024 -> {
			String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024))
		}
		bytesPerSecond >= 1024 -> {
			String.format("%.2f KB/s", bytesPerSecond / 1024.0)
		}
		else -> {
			String.format("%.0f B/s", bytesPerSecond)
		}
	}
}

/**
 * 通用圆形进度条组
 */
@Composable
fun CircularProgress(
	progress: Float,
	modifier: Modifier = Modifier
) {
	val progressColor = when {
		progress >= 0.9f -> Color(0xFFD32F2F) // 红色 - 90%以上
		progress >= 0.7f -> Color(0xFFFF9800) // 橙色 - 70-90%
		else -> MaterialTheme.colorScheme.primary // 正常颜色
	}
	val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
	
	Canvas(modifier = modifier) {
		val strokeWidth = 12.dp.toPx()
		val radius = (size.minDimension - strokeWidth) / 2f
		val center = Offset(size.width / 2f, size.height / 2f)
		
		// 绘制背景
		drawCircle(
			color = backgroundColor,
			radius = radius,
			center = center,
			style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
		)
		
		// 绘制进度�?
		val sweepAngle = 360f * progress
		drawArc(
			color = progressColor,
			startAngle = -90f, // 从顶部开始
			sweepAngle = sweepAngle,
			useCenter = false,
			topLeft = Offset(center.x - radius, center.y - radius),
			size = Size(radius * 2f, radius * 2f),
			style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
		)
	}
}

/**
 * 圆形磁盘使用率进度条（保持向后兼容）
 */
@Composable
fun CircularDiskProgress(
	progress: Float,
	modifier: Modifier = Modifier
) {
	CircularProgress(progress = progress, modifier = modifier)
}

