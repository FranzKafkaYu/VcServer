package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.network.PingSample
import com.franzkafkayu.vcserver.network.pingRowStats
import com.franzkafkayu.vcserver.ui.viewmodels.PingViewModel

private val BarGreen = Color(0xFF2E7D32)
private val BarRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
	viewModel: PingViewModel,
	onBackClick: () -> Unit
) {
	val state = viewModel.uiState.collectAsStateWithLifecycle().value
	val run = state.run
	Scaffold(
		topBar = {
			androidx.compose.material3.TopAppBar(
				title = { Text(stringResource(R.string.ping_title)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(state.serverName, style = MaterialTheme.typography.titleMedium)
			Text(
				text = run.address?.let { stringResource(R.string.traceroute_target, state.host, it) } ?: state.host,
				style = MaterialTheme.typography.bodyMedium
			)
			Text(
				text = stringResource(R.string.ping_not_ssh),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			when {
				run.resolveFailed -> {
					Text(
						text = stringResource(R.string.traceroute_resolve_failed, state.host),
						color = MaterialTheme.colorScheme.error
					)
				}
				else -> {
					if (run.running) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
							Text(stringResource(R.string.ping_running))
						}
					}
					run.remoteError?.let {
						Text(
							text = stringResource(R.string.ping_remote_failed),
							color = MaterialTheme.colorScheme.error
						)
					}
					LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
						item(key = "local") {
							PingRow(
								place = stringResource(R.string.ping_local),
								line = stringResource(R.string.ping_local_line),
								pending = false,
								resolveFailed = false,
								unavailable = run.localUnavailable,
								samples = run.localSamples
							)
						}
						items(run.nodes, key = { it.id }) { node ->
							PingRow(
								place = node.place,
								line = node.line,
								pending = node.pending,
								resolveFailed = node.resolveFailed,
								unavailable = false,
								samples = node.samples
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun PingRow(
	place: String,
	line: String,
	pending: Boolean,
	resolveFailed: Boolean,
	unavailable: Boolean,
	samples: List<PingSample>
) {
	val stats = pingRowStats(pending, resolveFailed, samples)
	val maxRtt = samples.mapNotNull { (it as? PingSample.Ok)?.rttMs }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(place, style = MaterialTheme.typography.bodyMedium)
				if (line.isNotBlank()) {
					Text(line, style = MaterialTheme.typography.bodySmall)
				}
			}
			val loss = stats.lossRatio
			Text(
				text = if (loss == null) {
					stringResource(R.string.ping_dash)
				} else {
					stringResource(R.string.ping_loss, loss * 100.0)
				},
				color = when {
					loss == null -> MaterialTheme.colorScheme.onSurface
					loss > 0.0 -> BarRed
					else -> BarGreen
				}
			)
			Text(stringResource(R.string.ping_sent, stats.finished))
		}
		if (unavailable) {
			Text(stringResource(R.string.ping_local_failed), color = MaterialTheme.colorScheme.error)
		} else if (resolveFailed) {
			Text(stringResource(R.string.ping_node_failed), color = MaterialTheme.colorScheme.error)
		} else if (samples.isNotEmpty()) {
			val dash = stringResource(R.string.ping_dash)
			val samplePattern = stringResource(R.string.ping_sample_ms)
			Text(samples.joinToString("  ") { sample ->
				when (sample) {
					is PingSample.Ok -> samplePattern.format(sample.rttMs)
					PingSample.Failed -> dash
				}
			})
			Text(
				stats.jitterMs?.let { stringResource(R.string.ping_jitter, it) }
					?: stringResource(R.string.ping_dash)
			)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(28.dp),
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.Bottom
			) {
				samples.forEach { sample ->
					val fraction = when (sample) {
						is PingSample.Ok -> (sample.rttMs.toFloat() / maxRtt.toFloat()).coerceIn(0.15f, 1f)
						PingSample.Failed -> 1f
					}
					val color = if (sample is PingSample.Ok) BarGreen else BarRed
					Box(
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.background(MaterialTheme.colorScheme.surfaceVariant),
						contentAlignment = Alignment.BottomCenter
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.fillMaxHeight(fraction)
								.background(color)
						)
					}
				}
			}
		}
	}
}
