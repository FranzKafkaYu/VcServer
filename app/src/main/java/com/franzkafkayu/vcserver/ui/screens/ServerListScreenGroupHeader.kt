package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.franzkafkayu.vcserver.R

/**
 * 分组标题组件
 */
@Composable
fun GroupHeader(
	groupName: String,
	serverCount: Int,
	isExpanded: Boolean,
	onClick: () -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(
					Icons.Default.Folder,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary
				)
				Column {
					Text(
						text = groupName,
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = stringResource(R.string.group_servers_count, serverCount),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			Icon(
				if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
				contentDescription = null
			)
		}
	}
}
