package com.franzkafkayu.vcserver.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.BuildConfig
import com.franzkafkayu.vcserver.R

/**
 * 关于界面
 */
object AboutScreenConstants {
	/**
	 * GitHub 仓库地址
	 */
	const val GITHUB_URL = "https://github.com/FranzKafkaYu/VcServer"
	
	/**
	 * 应用发布日期
	 */
	const val RELEASE_DATE = "2025-01-05"
	
	/**
	 * 作者名称
	 */
	const val AUTHOR = "FranzKafkaYu"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
	onBackClick: () -> Unit
) {
	val context = LocalContext.current

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.about_title)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(
							Icons.Default.ArrowBack,
							contentDescription = stringResource(R.string.back)
						)
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
				text = stringResource(R.string.about_app_name),
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
					InfoRow(stringResource(R.string.about_version_name), BuildConfig.VERSION_NAME)
					InfoRow(stringResource(R.string.about_version_code), BuildConfig.VERSION_CODE.toString())
					InfoRow(stringResource(R.string.about_commit), BuildConfig.GIT_COMMIT_HASH)
					InfoRow(stringResource(R.string.about_build_date), BuildConfig.BUILD_DATE)
					InfoRow(stringResource(R.string.about_author), AboutScreenConstants.AUTHOR)
				}
			}

			// GitHub 链接
			Card(
				modifier = Modifier.fillMaxWidth(),
				onClick = {
					val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AboutScreenConstants.GITHUB_URL))
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
						text = stringResource(R.string.about_github),
						style = MaterialTheme.typography.bodySmall
					)
					Text(
						text = AboutScreenConstants.GITHUB_URL,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}

			// 应用描述
			Text(
				text = stringResource(R.string.about_description),
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
