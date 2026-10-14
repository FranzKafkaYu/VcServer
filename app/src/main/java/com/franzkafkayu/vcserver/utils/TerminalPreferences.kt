package com.franzkafkayu.vcserver.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.terminalDataStore: DataStore<Preferences> by preferencesDataStore(name = "terminal_prefs")

/**
 * 终端字号与按服务器保存的命令历史。不进入数据库导入导出。
 */
class TerminalPreferences(private val context: Context) {
	suspend fun fontSizeSp(): Int {
		val stored = context.terminalDataStore.data.first()[FONT_SIZE_KEY] ?: TerminalFont.DEFAULT_SP
		return TerminalFont.coerce(stored)
	}

	suspend fun setFontSizeSp(sp: Int) {
		val value = TerminalFont.coerce(sp)
		context.terminalDataStore.edit { preferences ->
			preferences[FONT_SIZE_KEY] = value
		}
	}

	suspend fun history(serverId: Long): List<String> {
		val raw = context.terminalDataStore.data.first()[historyKey(serverId)]
		return CommandHistoryCodec.decode(raw)
	}

	suspend fun saveHistory(serverId: Long, commands: List<String>) {
		val encoded = CommandHistoryCodec.encode(commands)
		context.terminalDataStore.edit { preferences ->
			preferences[historyKey(serverId)] = encoded
		}
	}

	private fun historyKey(serverId: Long) = stringPreferencesKey("history_$serverId")

	companion object {
		private val FONT_SIZE_KEY = intPreferencesKey("font_size_sp")
	}
}
