package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.R
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
	onBackClick: () -> Unit
) {
	val context = LocalContext.current
	val language = LocalConfiguration.current.locales[0].language
	val assetName = if (language.startsWith("zh")) "privacy_zh.md" else "privacy_en.md"
	val body = remember(assetName) {
		context.assets.open(assetName).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.privacy_policy_title)) },
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
		Text(
			text = body,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.verticalScroll(rememberScrollState())
				.padding(24.dp)
		)
	}
}
