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
import com.franzkafkayu.vcserver.services.BandwagonServiceInfo
import com.franzkafkayu.vcserver.services.formatBandwagonBytes
import java.text.DateFormat
import java.util.Date

@Composable
fun BandwagonServiceInfoPanel(info: BandwagonServiceInfo) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		info.hostname?.let { InfoRow(stringResource(R.string.bandwagon_info_hostname), it) }
		info.nodeLocation?.let { InfoRow(stringResource(R.string.bandwagon_info_location), it) }
		info.plan?.let { InfoRow(stringResource(R.string.bandwagon_info_plan), it) }
		val used = info.dataCounter
		val cap = info.monthlyDataBytes
		if (used != null && cap != null) {
			InfoRow(
				stringResource(R.string.bandwagon_info_data),
				stringResource(
					R.string.bandwagon_info_data_value,
					formatBandwagonBytes(used),
					formatBandwagonBytes(cap)
				)
			)
		} else if (used != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), formatBandwagonBytes(used))
		} else if (cap != null) {
			InfoRow(stringResource(R.string.bandwagon_info_data), formatBandwagonBytes(cap))
		}
		info.dataNextReset?.let { reset ->
			val text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(reset * 1000L))
			InfoRow(stringResource(R.string.bandwagon_info_data_reset), text)
		}
		info.planDisk?.let { InfoRow(stringResource(R.string.bandwagon_info_disk), formatBandwagonBytes(it)) }
		info.planRam?.let { InfoRow(stringResource(R.string.bandwagon_info_ram), formatBandwagonBytes(it)) }
		info.os?.let { InfoRow(stringResource(R.string.bandwagon_info_os), it) }
		if (info.ipAddresses.isNotEmpty()) {
			InfoRow(stringResource(R.string.bandwagon_info_ips), info.ipAddresses.joinToString(", "))
		}
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
