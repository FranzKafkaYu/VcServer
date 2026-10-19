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
import com.franzkafkayu.vcserver.services.GreencloudInstanceInfo

@Composable
fun GreencloudInstanceInfoPanel(info: GreencloudInstanceInfo) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		info.name?.let { InfoRow(stringResource(R.string.greencloud_info_name), it) }
		info.hostname?.let { InfoRow(stringResource(R.string.bandwagon_info_hostname), it) }
		info.cpu?.let { InfoRow(stringResource(R.string.cloudcone_info_cpu), it) }
		info.ram?.let { InfoRow(stringResource(R.string.bandwagon_info_ram), it) }
		info.disk?.let { InfoRow(stringResource(R.string.bandwagon_info_disk), it) }
		val used = info.trafficUsed
		val limit = info.trafficLimit
		if (used != null && limit != null) {
			InfoRow(
				stringResource(R.string.bandwagon_info_data),
				stringResource(R.string.bandwagon_info_data_value, used, limit)
			)
		} else if (used != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), used)
		} else if (limit != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), limit)
		}
		if (info.ipAddresses.isNotEmpty()) {
			InfoRow(stringResource(R.string.bandwagon_info_ips), info.ipAddresses.joinToString(", "))
		}
		info.powerStatus?.let { InfoRow(stringResource(R.string.cloudcone_info_power), it) }
		info.suspended?.let {
			InfoRow(stringResource(R.string.greencloud_info_suspended), yesNo(it))
		}
		info.rescue?.let {
			InfoRow(stringResource(R.string.greencloud_info_rescue), yesNo(it))
		}
		info.vncEnabled?.let {
			InfoRow(stringResource(R.string.greencloud_info_vnc), yesNo(it))
		}
	}
}

@Composable
private fun yesNo(value: Boolean): String {
	return stringResource(if (value) R.string.greencloud_info_yes else R.string.greencloud_info_no)
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
