package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.CustomShellCommand
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.repositories.ServerGroupRepository
import com.franzkafkayu.vcserver.services.ServerManagementService
import com.franzkafkayu.vcserver.services.SettingsService
import com.franzkafkayu.vcserver.utils.CustomShellCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CustomShellCommandsUiState(
	val commands: List<CustomShellCommand> = emptyList(),
	val servers: List<Server> = emptyList(),
	val groups: List<ServerGroup> = emptyList(),
	val errorMessage: String? = null
)

class CustomShellCommandsViewModel(
	private val settingsService: SettingsService,
	private val serverManagementService: ServerManagementService,
	private val serverGroupRepository: ServerGroupRepository
) : ViewModel() {

	private val _uiState = MutableStateFlow(CustomShellCommandsUiState())
	val uiState: StateFlow<CustomShellCommandsUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			combine(
				settingsService.getSettings(),
				serverManagementService.getAllServers(),
				serverGroupRepository.getAllGroups()
			) { settings, servers, groups ->
				Triple(settings.customShellCommands, servers, groups)
			}.collect { (commands, servers, groups) ->
				_uiState.value = _uiState.value.copy(
					commands = commands,
					servers = servers,
					groups = groups
				)
			}
		}
	}

	fun saveCommand(
		id: String?,
		title: String,
		body: String,
		scopeType: String,
		scopeId: Long?
	): Boolean {
		val state = _uiState.value
		val next = CustomShellCommands.save(
			existing = state.commands,
			id = id,
			title = title,
			body = body,
			scopeType = scopeType,
			scopeId = scopeId,
			groupIds = state.groups.map { it.id }.toSet(),
			serverIds = state.servers.map { it.id }.toSet()
		)
		if (next == null) {
			val error = if (title.trim().isEmpty() || body.trim().isEmpty()) {
				"CUSTOM_SHELL_COMMAND_INVALID"
			} else {
				"CUSTOM_SHELL_COMMAND_SCOPE_INVALID"
			}
			_uiState.value = state.copy(errorMessage = error)
			return false
		}
		viewModelScope.launch {
			try {
				settingsService.updateCustomShellCommands(next)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "UPDATE_CUSTOM_SHELL_COMMANDS_FAILED:${e.message}"
				)
			}
		}
		return true
	}

	fun deleteCommand(id: String) {
		val next = CustomShellCommands.remove(_uiState.value.commands, id)
		viewModelScope.launch {
			try {
				settingsService.updateCustomShellCommands(next)
			} catch (e: Exception) {
				_uiState.value = _uiState.value.copy(
					errorMessage = "UPDATE_CUSTOM_SHELL_COMMANDS_FAILED:${e.message}"
				)
			}
		}
	}

	fun clearError() {
		_uiState.value = _uiState.value.copy(errorMessage = null)
	}
}
