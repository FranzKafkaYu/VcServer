package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.VirtualizorAccount
import com.franzkafkayu.vcserver.models.VirtualizorLink
import com.franzkafkayu.vcserver.services.ServerManagementService
import com.franzkafkayu.vcserver.services.VirtualizorAccountResult
import com.franzkafkayu.vcserver.services.VirtualizorBandwidth
import com.franzkafkayu.vcserver.services.VirtualizorCoordinator
import com.franzkafkayu.vcserver.services.VirtualizorTrafficResult
import com.franzkafkayu.vcserver.services.VirtualizorVps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class VirtualizorInstanceRow(
	val vps: VirtualizorVps,
	val linkedServerId: Long?
)

data class VirtualizorAccountUiState(
	val account: VirtualizorAccount? = null,
	val host: String = "",
	val port: String = VirtualizorAccount.DEFAULT_PORT.toString(),
	val apiKey: String = "",
	val apiPass: String = "",
	val instances: List<VirtualizorInstanceRow> = emptyList(),
	val servers: List<Server> = emptyList(),
	val pendingPin: String? = null,
	val noticeMessageRes: Int? = null,
	val noticeMessageArg: String? = null,
	val confirmDelete: Boolean = false,
	val busy: Boolean = false,
	val bandwidth: VirtualizorBandwidth? = null
)

class VirtualizorAccountViewModel(
	private val virtualizor: VirtualizorCoordinator,
	private val serverManagementService: ServerManagementService
) : ViewModel() {
	private val _uiState = MutableStateFlow(VirtualizorAccountUiState())
	val uiState: StateFlow<VirtualizorAccountUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			combine(
				virtualizor.observeAccount(),
				virtualizor.observeLinks(),
				serverManagementService.getAllServers()
			) { account, links, servers ->
				Triple(account, links, servers)
			}.collect { (account, links, servers) ->
				_uiState.value = _uiState.value.copy(
					account = account,
					host = account?.host ?: _uiState.value.host,
					port = (account?.port ?: VirtualizorAccount.DEFAULT_PORT).toString(),
					servers = servers
				)
				refreshInstances(links)
			}
		}
	}

	fun updateHost(value: String) {
		_uiState.value = _uiState.value.copy(host = value)
	}

	fun updatePort(value: String) {
		_uiState.value = _uiState.value.copy(port = value)
	}

	fun updateApiKey(value: String) {
		_uiState.value = _uiState.value.copy(apiKey = value)
	}

	fun updateApiPass(value: String) {
		_uiState.value = _uiState.value.copy(apiPass = value)
	}

	fun save(confirmedPin: String? = null) {
		val state = _uiState.value
		val port = state.port.toIntOrNull() ?: return
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(busy = true, pendingPin = null)
			when (val result = virtualizor.saveAccount(state.host, port, state.apiKey, state.apiPass, confirmedPin)) {
				VirtualizorAccountResult.Success -> {
					_uiState.value = _uiState.value.copy(
						busy = false,
						apiKey = "",
						apiPass = "",
						noticeMessageRes = R.string.virtualizor_account_saved,
						noticeMessageArg = null
					)
				}
				is VirtualizorAccountResult.NeedsCertificatePin -> {
					_uiState.value = _uiState.value.copy(busy = false, pendingPin = result.sha256)
				}
				is VirtualizorAccountResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						busy = false,
						noticeMessageRes = R.string.virtualizor_account_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun confirmPin() {
		val pin = _uiState.value.pendingPin ?: return
		save(pin)
	}

	fun dismissPin() {
		_uiState.value = _uiState.value.copy(pendingPin = null)
	}

	fun requestDelete() {
		_uiState.value = _uiState.value.copy(confirmDelete = true)
	}

	fun dismissDelete() {
		_uiState.value = _uiState.value.copy(confirmDelete = false)
	}

	fun deleteAccount() {
		viewModelScope.launch {
			virtualizor.deleteAccount()
			_uiState.value = _uiState.value.copy(
				confirmDelete = false,
				noticeMessageRes = R.string.virtualizor_account_deleted,
				noticeMessageArg = null
			)
		}
	}

	fun consumeNotice() {
		_uiState.value = _uiState.value.copy(noticeMessageRes = null, noticeMessageArg = null)
	}

	fun queryBandwidth(vpsid: String) {
		viewModelScope.launch {
			when (val result = virtualizor.bandwidth(vpsid)) {
				is VirtualizorTrafficResult.Known -> {
					_uiState.value = _uiState.value.copy(bandwidth = result.bandwidth)
				}
				VirtualizorTrafficResult.Unknown -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.virtualizor_bandwidth_unknown,
						noticeMessageArg = null
					)
				}
				VirtualizorTrafficResult.NoAccount -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.virtualizor_no_account,
						noticeMessageArg = null
					)
				}
				is VirtualizorTrafficResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.virtualizor_bandwidth_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun dismissBandwidth() {
		_uiState.value = _uiState.value.copy(bandwidth = null)
	}

	private suspend fun refreshInstances(links: List<VirtualizorLink>) {
		if (_uiState.value.account == null) {
			_uiState.value = _uiState.value.copy(instances = emptyList())
			return
		}
		val listed = virtualizor.listInstances()
		if (!listed.ok) {
			_uiState.value = _uiState.value.copy(
				noticeMessageRes = R.string.virtualizor_list_failed,
				noticeMessageArg = listed.message
			)
			return
		}
		val byVps = links.associateBy { it.vpsid }
		_uiState.value = _uiState.value.copy(
			instances = listed.instances.map { vps ->
				VirtualizorInstanceRow(vps = vps, linkedServerId = byVps[vps.id]?.serverId)
			}
		)
	}
}
