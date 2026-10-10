package com.franzkafkayu.vcserver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.franzkafkayu.vcserver.models.ThemeMode

private val DarkColorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFFFD700), // 深色模式使用更亮的金黄色 #FFD700
	secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
	tertiary = androidx.compose.ui.graphics.Color(0xFF3700B3)
)

private val LightColorScheme = lightColorScheme(
	primary = androidx.compose.ui.graphics.Color(0xFFFFA500), // 橙黄色 #FFA500，确保文字清晰可读
	secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
	tertiary = androidx.compose.ui.graphics.Color(0xFF3700B3)
)

@Composable
fun VcServerTheme(
	themeMode: ThemeMode = ThemeMode.SYSTEM,
	content: @Composable () -> Unit
) {
	val systemDarkTheme = isSystemInDarkTheme()
	val darkTheme = when (themeMode) {
		ThemeMode.LIGHT -> false
		ThemeMode.DARK -> true
		ThemeMode.SYSTEM -> systemDarkTheme
	}
	
	val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

	MaterialTheme(
		colorScheme = colorScheme,
		typography = androidx.compose.material3.Typography(),
		content = content
	)
}



