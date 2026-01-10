package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.repositories.ServerGroupRepository
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 服务器分组管理 ViewModel
 */
class ServerGroupManagementViewModel(
	private val serverGroupRepository: ServerGroupRepository
) : ViewModel() {
	private val _uiState = MutableStateFlow(ServerGroupManagementUiState())
	val uiState: StateFlow<ServerGroupManagementUiState> = _uiState.asStateFlow()

	init {
		loadGroups()
	}

	/**
	 * 加载分组列表
	 */
	private fun loadGroups() {
		viewModelScope.launch {
			serverGroupRepository.getAllGroups().collect { groups ->
				_uiState.value = _uiState.value.copy(
					groups = groups,
					isLoading = false
				)
			}
		}
	}

	/**
	 * 显示创建分组对话框
	 */
	fun showCreateGroupDialog() {
		_uiState.value = _uiState.value.copy(
			isCreateGroupDialogVisible = true,
			editingGroup = null,
			groupNameInput = "",
			error = null
		)
	}

	/**
	 * 显示编辑分组对话框
	 */
	fun showEditGroupDialog(group: ServerGroup) {
		_uiState.value = _uiState.value.copy(
			isCreateGroupDialogVisible = false,
			isEditGroupDialogVisible = true,
			editingGroup = group,
			groupNameInput = group.name
		)
	}

	/**
	 * 隐藏创建分组对话框
	 */
	fun hideCreateGroupDialog() {
		_uiState.value = _uiState.value.copy(
			isCreateGroupDialogVisible = false,
			groupNameInput = "",
			error = null
		)
	}

	/**
	 * 隐藏编辑分组对话框
	 */
	fun hideEditGroupDialog() {
		_uiState.value = _uiState.value.copy(
			isEditGroupDialogVisible = false,
			editingGroup = null,
			groupNameInput = ""
		)
	}

	/**
	 * 更新分组名称输入
	 */
	fun updateGroupNameInput(name: String) {
		_uiState.value = _uiState.value.copy(
			groupNameInput = name,
			error = null // 用户输入时清除错误
		)
	}

	/**
	 * 创建分组
	 */
	fun createGroup() {
		val name = _uiState.value.groupNameInput.trim()
		if (name.isEmpty()) {
			_uiState.value = _uiState.value.copy(
				error = AppError.ValidationError("GROUP_NAME_EMPTY")
			)
			return
		}

		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, error = null)
			val result = serverGroupRepository.createGroup(name)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						isCreateGroupDialogVisible = false,
						groupNameInput = "",
						error = null
					)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 更新分组
	 */
	fun updateGroup() {
		val editingGroup = _uiState.value.editingGroup ?: return
		val name = _uiState.value.groupNameInput.trim()
		if (name.isEmpty()) {
			_uiState.value = _uiState.value.copy(
				error = AppError.ValidationError("GROUP_NAME_EMPTY")
			)
			return
		}

		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, error = null)
			val updatedGroup = editingGroup.copy(name = name)
			val result = serverGroupRepository.updateGroup(updatedGroup)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						isEditGroupDialogVisible = false,
						editingGroup = null,
						groupNameInput = "",
						error = null
					)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 显示删除确认对话框
	 */
	fun showDeleteConfirmDialog(group: ServerGroup) {
		_uiState.value = _uiState.value.copy(
			groupToDelete = group
		)
	}

	/**
	 * 取消删除
	 */
	fun cancelDelete() {
		_uiState.value = _uiState.value.copy(
			groupToDelete = null
		)
	}

	/**
	 * 确认删除分组
	 */
	fun confirmDeleteGroup() {
		val group = _uiState.value.groupToDelete ?: return
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isLoading = true,
				groupToDelete = null,
				error = null
			)
			val result = serverGroupRepository.deleteGroup(group.id)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = null
					)
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 清除错误
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}
}

/**
 * 分组管理 UI 状态
 */
data class ServerGroupManagementUiState(
	val groups: List<ServerGroup> = emptyList(),
	val isLoading: Boolean = false,
	val isCreateGroupDialogVisible: Boolean = false,
	val isEditGroupDialogVisible: Boolean = false,
	val editingGroup: ServerGroup? = null,
	val groupNameInput: String = "",
	val groupToDelete: ServerGroup? = null,
	val error: AppError? = null
)
