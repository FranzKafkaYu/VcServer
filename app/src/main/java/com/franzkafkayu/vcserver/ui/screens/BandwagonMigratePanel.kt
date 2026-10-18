package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.services.BandwagonMigrateLocation
import com.franzkafkayu.vcserver.services.BandwagonMigrateLocations
import com.franzkafkayu.vcserver.services.BandwagonMigrateStart
import java.util.Locale

@Composable
fun BandwagonMigrateLocationList(
	locations: BandwagonMigrateLocations,
	onPick: (BandwagonMigrateLocation) -> Unit
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		locations.options.forEach { option ->
			val current = option.id == locations.currentLocation
			val title = option.description?.takeIf { it.isNotBlank() } ?: option.id
			val multiplier = option.dataTransferMultiplier?.let { value ->
				stringResource(R.string.bandwagon_migrate_multiplier, formatMultiplier(value))
			}
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.then(if (current) Modifier else Modifier.clickable { onPick(option) })
					.padding(vertical = 8.dp)
			) {
				Text(
					text = if (current) {
						stringResource(R.string.bandwagon_migrate_current, title)
					} else {
						title
					},
					style = MaterialTheme.typography.bodyLarge,
					color = if (current) {
						MaterialTheme.colorScheme.onSurfaceVariant
					} else {
						MaterialTheme.colorScheme.onSurface
					}
				)
				if (multiplier != null) {
					Text(
						text = multiplier,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

@Composable
fun BandwagonMigrateResultPanel(
	start: BandwagonMigrateStart,
	onUseHost: (String) -> Unit
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Text(
			text = stringResource(R.string.bandwagon_migrate_accepted),
			style = MaterialTheme.typography.bodyMedium
		)
		start.notificationEmail?.let { email ->
			Text(
				text = stringResource(R.string.bandwagon_migrate_email, email),
				style = MaterialTheme.typography.bodyMedium
			)
		}
		start.newIps.forEach { ip ->
			Text(
				text = stringResource(R.string.bandwagon_migrate_use_host, ip),
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onUseHost(ip) }
					.padding(vertical = 6.dp)
			)
		}
	}
}

private fun formatMultiplier(value: Double): String {
	return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%s", value)
}
