package com.vcserver.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager

/**
 * 关于界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
	onBackClick: () -> Unit
) {
	val context = LocalContext.current

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("关于") },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
				.padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(24.dp)
		) {
			// 应用图标和名称
			Text(
				text = "VcServer",
				style = MaterialTheme.typography.headlineLarge,
				fontWeight = FontWeight.Bold
			)

			// 版本信息
			Card(
				modifier = Modifier.fillMaxWidth()
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					val packageInfo = try {
						context.packageManager.getPackageInfo(context.packageName, 0)
					} catch (e: Exception) {
						null
					}
					InfoRow("版本号", packageInfo?.versionName ?: "1.0")
					InfoRow("版本代码", (packageInfo?.versionCode ?: 1).toString())
					InfoRow("发布时间", "2025-01-05")
					InfoRow("作者", "VcServer Team")
				}
			}

			// GitHub 链接
			Card(
				modifier = Modifier.fillMaxWidth(),
				onClick = {
					val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vcserver/vcserver"))
					context.startActivity(intent)
				}
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "GitHub",
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = "https://github.com/vcserver/vcserver",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}

			// 应用描述
			Text(
				text = "VcServer 是一个 Android 应用，通过 SSH 连接到远程服务器并进行管理。",
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}

@Composable
private fun InfoRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium
		)
	}
}

