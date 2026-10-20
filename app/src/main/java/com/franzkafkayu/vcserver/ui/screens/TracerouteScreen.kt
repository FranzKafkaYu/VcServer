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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.network.TraceHop
import com.franzkafkayu.vcserver.ui.viewmodels.TracerouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
	viewModel: TracerouteViewModel,
	onBackClick: () -> Unit
) {
	val state = viewModel.uiState.collectAsStateWithLifecycle().value
	val maxRtt = state.hops.mapNotNull { it.rttMs }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.traceroute_title)) },
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
				text = state.resolvedAddress?.let { ip ->
					stringResource(R.string.traceroute_target, state.host, ip)
				} ?: state.host,
				style = MaterialTheme.typography.bodyMedium
			)
			state.hostEchoMs?.let { echo ->
				Text(
					text = stringResource(R.string.traceroute_host_echo, echo),
					style = MaterialTheme.typography.bodyMedium
				)
			}
			state.tcpMs?.let { tcp ->
				Text(
					text = stringResource(R.string.traceroute_tcp_ms, tcp),
					style = MaterialTheme.typography.bodyMedium
				)
			}
			Text(
				text = stringResource(R.string.traceroute_not_ssh),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			when {
				state.resolveFailed -> {
					Text(
						text = stringResource(R.string.traceroute_resolve_failed, state.host),
						color = MaterialTheme.colorScheme.error
					)
				}
				state.errorMessage != null && state.hops.isEmpty() -> {
					Text(
						text = state.errorMessage,
						color = MaterialTheme.colorScheme.error
					)
				}
				else -> {
					if (state.running) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
							Text(stringResource(R.string.traceroute_running))
						}
					}
					if (state.reachedTarget) {
						Text(stringResource(R.string.traceroute_reached))
					} else if (!state.running && (state.hopsUnavailable || state.hops.isNotEmpty())) {
						Text(
							text = stringResource(R.string.traceroute_no_hops),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					LazyColumn(
						verticalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier.fillMaxSize()
					) {
						items(state.hops, key = { it.ttl }) { hop ->
							HopRow(hop = hop, maxRtt = maxRtt)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun HopRow(hop: TraceHop, maxRtt: Long) {
	val label = when {
		hop.timedOut -> stringResource(R.string.traceroute_hop_timeout)
		else -> hop.address ?: stringResource(R.string.traceroute_hop_timeout)
	}
	val rttText = hop.rttMs?.let { stringResource(R.string.traceroute_rtt_ms, it) } ?: ""
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text("${hop.ttl}  $label", style = MaterialTheme.typography.bodyMedium)
			if (rttText.isNotEmpty()) {
				Text(rttText, style = MaterialTheme.typography.bodySmall)
			}
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(8.dp)
				.background(MaterialTheme.colorScheme.surfaceVariant)
		) {
			if (hop.rttMs != null && !hop.timedOut) {
				val fraction = (hop.rttMs.toFloat() / maxRtt.toFloat()).coerceIn(0.05f, 1f)
				Box(
					modifier = Modifier
						.fillMaxHeight()
						.fillMaxWidth(fraction)
						.background(MaterialTheme.colorScheme.primary)
				)
			}
		}
	}
}
