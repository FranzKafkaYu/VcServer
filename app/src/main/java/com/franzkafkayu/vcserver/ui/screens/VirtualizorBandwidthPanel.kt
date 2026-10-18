package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.services.VirtualizorBandwidth
import com.franzkafkayu.vcserver.services.formatBandwidthGb

@Composable
fun VirtualizorBandwidthPanel(bandwidth: VirtualizorBandwidth) {
	val used = bandwidth.usedGb ?: 0.0
	val limit = bandwidth.limitGb ?: 0.0
	val percent = bandwidth.percent
		?: if (!bandwidth.unlimited && limit > 0) (used / limit) * 100.0 else 0.0
	val usageText = if (bandwidth.unlimited) {
		stringResource(R.string.virtualizor_bandwidth_used_unlimited, formatBandwidthGb(used))
	} else {
		stringResource(
			R.string.virtualizor_bandwidth_used_limited,
			formatBandwidthGb(used),
			formatBandwidthGb(limit)
		)
	}
	val inGb = bandwidth.inGb
	val outGb = bandwidth.outGb
	val totalIo = ((inGb ?: 0.0) + (outGb ?: 0.0)).takeIf { it > 0 }
	val inShare = if (inGb != null && totalIo != null) inGb / totalIo * 100.0 else null
	val outShare = if (outGb != null && totalIo != null) outGb / totalIo * 100.0 else null

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				imageVector = Icons.Default.Bolt,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(22.dp)
			)
			Spacer(modifier = Modifier.width(6.dp))
			Text(
				text = stringResource(R.string.virtualizor_bandwidth_title),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			Spacer(modifier = Modifier.width(8.dp))
			Text(
				text = usageText,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f)
			)
		}
		LinearProgressIndicator(
			progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
			modifier = Modifier
				.fillMaxWidth()
				.height(8.dp)
				.clip(RoundedCornerShape(50)),
		)
		if (inGb != null && outGb != null && inShare != null && outShare != null) {
			Text(
				text = stringResource(
					R.string.virtualizor_bandwidth_io,
					formatBandwidthGb(inGb),
					formatBandwidthGb(outGb)
				),
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			val inWeight = inShare.toFloat().coerceAtLeast(0.01f)
			val outWeight = outShare.toFloat().coerceAtLeast(0.01f)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(10.dp)
					.clip(RoundedCornerShape(50))
			) {
				Box(
					modifier = Modifier
						.weight(inWeight)
						.fillMaxHeight()
						.background(MaterialTheme.colorScheme.primary)
				)
				Box(
					modifier = Modifier
						.weight(outWeight)
						.fillMaxHeight()
						.background(MaterialTheme.colorScheme.tertiary)
				)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					text = stringResource(R.string.virtualizor_bandwidth_in),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary
				)
				Text(
					text = stringResource(R.string.virtualizor_bandwidth_out),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.tertiary
				)
			}
		}
	}
}
