package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.services.CloudconeInstanceInfo

@Composable
fun CloudconeInstanceInfoPanel(info: CloudconeInstanceInfo) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		info.hostname?.let { InfoRow(stringResource(R.string.bandwagon_info_hostname), it) }
		info.node?.let { InfoRow(stringResource(R.string.bandwagon_info_location), it) }
		info.os?.let { InfoRow(stringResource(R.string.bandwagon_info_os), it) }
		info.cpu?.let { InfoRow(stringResource(R.string.cloudcone_info_cpu), it) }
		info.ram?.let { InfoRow(stringResource(R.string.bandwagon_info_ram), it) }
		info.disk?.let { InfoRow(stringResource(R.string.bandwagon_info_disk), it) }
		val used = info.bandwidthUsed
		val total = info.bandwidthTotal
		if (used != null && total != null) {
			InfoRow(
				stringResource(R.string.bandwagon_info_data),
				stringResource(R.string.bandwagon_info_data_value, used, total)
			)
		} else if (used != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), used)
		} else if (total != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), total)
		}
		if (info.ipAddresses.isNotEmpty()) {
			InfoRow(stringResource(R.string.bandwagon_info_ips), info.ipAddresses.joinToString(", "))
		}
		info.powerStatus?.let { InfoRow(stringResource(R.string.cloudcone_info_power), it) }
	}
}

@Composable
private fun InfoRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(0.35f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(0.65f)
		)
	}
}
