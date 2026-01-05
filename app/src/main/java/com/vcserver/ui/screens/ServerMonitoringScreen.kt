package com.vcserver.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcserver.R
import com.vcserver.models.DiskInfo
import com.vcserver.ui.viewmodels.ServerMonitoringViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * 服务器监控界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerMonitoringScreen(
	viewModel: ServerMonitoringViewModel,
	onBackClick: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

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
						onClick = { viewModel.loadServerStatus() },
						enabled = !uiState.isLoading
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
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(R.string.cpu_cores))
									Text("${status.cpu.cores}", fontWeight = FontWeight.Medium)
								}
							}
							
							// 右侧：圆形进度图表
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
							
							// 右侧：圆形进度图表
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
		
		// 右侧：圆形进度图表
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
 * 通用圆形进度条组件
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
		
		// 绘制背景圆
		drawCircle(
			color = backgroundColor,
			radius = radius,
			center = center,
			style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
		)
		
		// 绘制进度圆
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

