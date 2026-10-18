package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.ssh.SshClientSession
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.repositories.ServerGroupRepository
import com.franzkafkayu.vcserver.services.BandwagonBindResult
import com.franzkafkayu.vcserver.services.BandwagonPowerAction
import com.franzkafkayu.vcserver.services.BandwagonPowerCoordinator
import com.franzkafkayu.vcserver.services.BandwagonPowerResult
import com.franzkafkayu.vcserver.services.BandwagonStatusResult
import com.franzkafkayu.vcserver.services.BandwagonServiceInfo
import com.franzkafkayu.vcserver.services.BandwagonServiceInfoResult
import com.franzkafkayu.vcserver.services.BandwagonMigrateLocation
import com.franzkafkayu.vcserver.services.BandwagonMigrateLocations
import com.franzkafkayu.vcserver.services.BandwagonMigrateLocationsResult
import com.franzkafkayu.vcserver.services.BandwagonMigrateStart
import com.franzkafkayu.vcserver.services.BandwagonMigrateStartResult
import com.franzkafkayu.vcserver.services.BandwagonResetRootResult
import com.franzkafkayu.vcserver.services.BandwagonUsageRange
import com.franzkafkayu.vcserver.services.BandwagonUsageResult
import com.franzkafkayu.vcserver.services.BandwagonUsageStats
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.services.CloudconeBindResult
import com.franzkafkayu.vcserver.services.CloudconeCoordinator
import com.franzkafkayu.vcserver.services.CloudconeInfoResult
import com.franzkafkayu.vcserver.services.CloudconeInstance
import com.franzkafkayu.vcserver.services.CloudconeInstanceInfo
import com.franzkafkayu.vcserver.services.CloudconePowerAction
import com.franzkafkayu.vcserver.services.CloudconePowerResult
import com.franzkafkayu.vcserver.services.CloudconeStatusResult
import com.franzkafkayu.vcserver.services.VirtualizorAccountResult
import com.franzkafkayu.vcserver.services.VirtualizorBindResult
import com.franzkafkayu.vcserver.services.VirtualizorCoordinator
import com.franzkafkayu.vcserver.services.VirtualizorPowerAction
import com.franzkafkayu.vcserver.services.VirtualizorPowerResult
import com.franzkafkayu.vcserver.services.VirtualizorStatusResult
import com.franzkafkayu.vcserver.services.VirtualizorTrafficResult
import com.franzkafkayu.vcserver.services.VirtualizorBandwidth
import com.franzkafkayu.vcserver.services.VirtualizorVps
import com.franzkafkayu.vcserver.services.ServerManagementService
import com.franzkafkayu.vcserver.services.VendorKind
import com.franzkafkayu.vcserver.services.VendorMenuEntry
import com.franzkafkayu.vcserver.services.ServerMonitoringService
import com.franzkafkayu.vcserver.utils.AppError
import com.franzkafkayu.vcserver.utils.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.utils.FilterQueryParser
import com.franzkafkayu.vcserver.utils.ServerFilter

/**
 * 服务器列�?ViewModel
 */
class ServerListViewModel(
	private val serverManagementService: ServerManagementService,
	private val serverMonitoringService: ServerMonitoringService,
	private val serverGroupRepository: ServerGroupRepository,
	private val bandwagon: BandwagonPowerCoordinator,
	private val cloudcone: CloudconeCoordinator,
	private val virtualizor: VirtualizorCoordinator
) : ViewModel() {
	private val _uiState = MutableStateFlow(ServerListUiState())
	val uiState: StateFlow<ServerListUiState> = _uiState.asStateFlow()

	init {
		loadServers()
		viewModelScope.launch {
			bandwagon.observeBoundServerIds().collect { ids ->
				bandwagonIds = ids
				publishVendors()
			}
		}
		viewModelScope.launch {
			cloudcone.observeLinks().collect { links ->
				cloudconeLinkRows = links
				cloudconeIds = links.map { it.serverId }.toSet()
				publishVendors()
			}
		}
		viewModelScope.launch {
			virtualizor.observeLinks().collect { links ->
				virtualizorLinkRows = links
				virtualizorIds = links.map { it.serverId }.toSet()
				publishVendors()
			}
		}
	}

	private var bandwagonIds: Set<Long> = emptySet()
	private var cloudconeIds: Set<Long> = emptySet()
	private var cloudconeLinkRows: List<com.franzkafkayu.vcserver.models.CloudconeLink> = emptyList()
	private var virtualizorIds: Set<Long> = emptySet()
	private var virtualizorLinkRows: List<com.franzkafkayu.vcserver.models.VirtualizorLink> = emptyList()

	private fun publishVendors() {
		val vendors = buildMap {
			bandwagonIds.forEach { put(it, VendorKind.BANDWAGON) }
			cloudconeIds.forEach { put(it, VendorKind.CLOUDCONE) }
			virtualizorIds.forEach { put(it, VendorKind.VIRTUALIZOR) }
		}
		_uiState.value = _uiState.value.copy(serverVendors = vendors)
	}

	/**
	 * 加载服务器列�?
	 */
	private fun loadServers() {
		loadServersAndGroups()
	}

	/**
	 * 加载服务器列表和分组
	 */
	private fun loadServersAndGroups() {
		viewModelScope.launch {
			combine(
				serverManagementService.getAllServers(),
				serverGroupRepository.getAllGroups(),
				_uiState.map { it.searchQuery }
			) { servers: List<Server>, groups: List<ServerGroup>, query: String ->
				val (filteredServers, syntaxError) = filterServers(servers, query)
				val orderedServers = filteredServers.sortedWith(Server.listOrderComparator)
				
				// 按分组ID分组服务器
				val groupedServersMap = orderedServers
					.filter { it.groupId != null }
					.groupBy { it.groupId!! }

				val groupedList = groups.map { group ->
					group to (groupedServersMap[group.id] ?: emptyList())
				}.filter { it.second.isNotEmpty() }

				val ungroupedList = orderedServers.filter { it.groupId == null }

				_uiState.value = _uiState.value.copy(
					servers = servers, // 保留完整列表用于选择模式
					groups = groups,
					groupedServers = groupedList,
					ungroupedServers = ungroupedList,
					searchSyntaxError = syntaxError, // 更新语法错误
					isLoading = false
				)
			}.collect { }
		}
	}
	
	/**
	 * 筛选服务器列表
	 * @return Pair<筛选后的服务器列表, 语法错误信息（如果有）>
	 */
	private fun filterServers(servers: List<Server>, query: String): Pair<List<Server>, String?> {
		if (query.isBlank()) return Pair(servers, null)
		
		val parser = FilterQueryParser()
		val filter = ServerFilter()
		
		// 检查是否是简单文本（不包含字段指定、AND、OR等）
		return if (parser.isSimpleText(query)) {
			// 简单文本模式：在所有字段中搜索
			Pair(servers.filter { filter.matchesSimpleText(it, query) }, null)
		} else {
			// 高级模式：解析查询语法
			val parseResult = parser.parse(query)
			parseResult.fold(
				onSuccess = { condition ->
					// 解析成功，无语法错误
					Pair(servers.filter { filter.matches(it, condition) }, null)
				},
				onFailure = { exception ->
					// 解析失败，回退到简单文本搜索
					Pair(
						servers.filter { filter.matchesSimpleText(it, query) },
						exception.message ?: "查询语法错误"
					)
				}
			)
		}
	}
	
	/**
	 * 更新搜索查询
	 */
	fun updateSearchQuery(query: String) {
		_uiState.value = _uiState.value.copy(
			searchQuery = query,
			searchSyntaxError = null
		)
	}
	
	/**
	 * 切换搜索栏状态
	 */
	fun toggleSearch() {
		val currentState = _uiState.value
		val newIsActive = !currentState.isSearchActive
		_uiState.value = currentState.copy(
			isSearchActive = newIsActive,
			// 关闭搜索时清空查询
			searchQuery = if (!newIsActive) "" else currentState.searchQuery,
			searchSyntaxError = null
		)
	}
	
	/**
	 * 清除搜索查询
	 */
	fun clearSearchQuery() {
		_uiState.value = _uiState.value.copy(
			searchQuery = "",
			searchSyntaxError = null
		)
	}

	/**
	 * 显示删除确认对话�?
	 */
	fun showDeleteConfirmDialog(server: Server) {
		if (_uiState.value.serverToDelete != null) return
		_uiState.value = _uiState.value.copy(
			serverToDelete = server
		)
	}

	/**
	 * 取消删除
	 */
	fun cancelDelete() {
		_uiState.value = _uiState.value.copy(
			serverToDelete = null
		)
	}

	/**
	 * 确认删除服务�?
	 */
	fun toggleServerPinned(server: Server) {
		viewModelScope.launch {
			val result = serverManagementService.toggleServerPinned(server)
			result.fold(
				onSuccess = { },
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						error = exception.toAppError()
					)
				}
			)
		}
	}

	fun duplicateServer(server: Server, copiedName: String) {
		viewModelScope.launch {
			val result = serverManagementService.duplicateServer(server, copiedName)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.server_copied
					)
				},
				onFailure = {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.server_copy_failed
					)
				}
			)
		}
	}

	fun clearNotice() {
		_uiState.value = _uiState.value.copy(noticeMessageRes = null, noticeMessageArg = null)
	}

	fun onVendorMenu(serverId: Long, entry: VendorMenuEntry) {
		val vendor = _uiState.value.serverVendors[serverId]
		when (entry) {
			VendorMenuEntry.BIND -> {
				_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Choose(serverId))
			}
			VendorMenuEntry.START -> startPower(serverId, vendor)
			VendorMenuEntry.STOP -> {
				if (vendor != null) {
					_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.ConfirmStop(serverId, vendor))
				}
			}
			VendorMenuEntry.RESTART -> {
				if (vendor != null) {
					_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.ConfirmRestart(serverId, vendor))
				}
			}
			VendorMenuEntry.STATUS -> queryStatus(serverId, vendor)
			VendorMenuEntry.SERVICE_INFO -> queryServiceInfo(serverId, vendor)
			VendorMenuEntry.MIGRATE -> queryMigrateLocations(serverId, vendor)
			VendorMenuEntry.RESET_ROOT -> {
				if (vendor == VendorKind.BANDWAGON) {
					_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.ConfirmResetRoot(serverId))
				}
			}
			VendorMenuEntry.USAGE -> queryUsage(serverId, vendor)
			VendorMenuEntry.BANDWIDTH -> queryBandwidth(serverId, vendor)
			VendorMenuEntry.UNBIND -> {
				if (vendor != null) {
					_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.ConfirmUnbind(serverId, vendor))
				}
			}
		}
	}

	fun dismissVendorPrompt() {
		_uiState.value = _uiState.value.copy(vendorPrompt = null)
	}

	fun chooseVendor(kind: VendorKind) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.Choose ?: return
		when (kind) {
			VendorKind.BANDWAGON -> {
				_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Bandwagon(prompt.serverId))
			}
			VendorKind.CLOUDCONE -> {
				_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.CloudconeSecrets(prompt.serverId))
			}
			VendorKind.VIRTUALIZOR -> {
				_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.VirtualizorSecrets(prompt.serverId))
			}
		}
	}

	fun submitBandwagonBind(veid: String, apiKey: String) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.Bandwagon ?: return
		viewModelScope.launch {
			when (val result = bandwagon.bind(prompt.serverId, veid, apiKey)) {
				BandwagonBindResult.Success -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = null,
						noticeMessageRes = R.string.bandwagon_bind_success,
						noticeMessageArg = null
					)
				}
				BandwagonBindResult.VeidInUse -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.bandwagon_veid_in_use,
						noticeMessageArg = null
					)
				}
				BandwagonBindResult.OtherVendor -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.vendor_already_bound,
						noticeMessageArg = null
					)
				}
				is BandwagonBindResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.bandwagon_bind_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun submitCloudconeAccount(apiKey: String, hash: String) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.CloudconeSecrets ?: return
		loadCloudconeInstances(prompt.serverId, apiKey, hash)
	}

	fun submitVirtualizorAccount(
		host: String,
		port: String,
		apiKey: String,
		apiPass: String,
		confirmedPin: String? = null
	) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.VirtualizorSecrets ?: return
		val parsedPort = port.toIntOrNull() ?: return
		viewModelScope.launch {
			when (val result = virtualizor.previewInstances(host, parsedPort, apiKey, apiPass, confirmedPin)) {
				VirtualizorAccountResult.Success -> loadVirtualizorInstances(
					prompt.serverId,
					host,
					parsedPort,
					apiKey,
					apiPass,
					confirmedPin
				)
				is VirtualizorAccountResult.NeedsCertificatePin -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = prompt.copy(pendingPin = result.sha256)
					)
				}
				is VirtualizorAccountResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.virtualizor_account_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun bindVirtualizorInstance(vpsid: String) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.VirtualizorPick ?: return
		viewModelScope.launch {
			when (val result = virtualizor.bind(
				prompt.serverId,
				vpsid,
				prompt.host,
				prompt.port,
				prompt.apiKey,
				prompt.apiPass,
				prompt.certPinSha256
			)) {
				VirtualizorBindResult.Success -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = null,
						noticeMessageRes = R.string.virtualizor_bind_success,
						noticeMessageArg = null
					)
				}
				VirtualizorBindResult.OtherVendor -> notice(R.string.vendor_already_bound)
				VirtualizorBindResult.InstanceInUse -> notice(R.string.virtualizor_instance_in_use)
				is VirtualizorBindResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.virtualizor_account_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun bindCloudconeInstance(instanceId: String) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.CloudconePick ?: return
		viewModelScope.launch {
			when (val result = cloudcone.bind(prompt.serverId, instanceId, prompt.apiKey, prompt.hash)) {
				CloudconeBindResult.Success -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = null,
						noticeMessageRes = R.string.cloudcone_bind_success,
						noticeMessageArg = null
					)
				}
				CloudconeBindResult.OtherVendor -> notice(R.string.vendor_already_bound)
				CloudconeBindResult.InstanceInUse -> notice(R.string.cloudcone_instance_in_use)
				is CloudconeBindResult.Failed -> {
					_uiState.value = _uiState.value.copy(
						noticeMessageRes = R.string.cloudcone_account_failed,
						noticeMessageArg = result.message
					)
				}
			}
		}
	}

	fun confirmVendorPower() {
		val prompt = _uiState.value.vendorPrompt ?: return
		when (prompt) {
			is VendorPrompt.ConfirmStop -> dispatchPower(prompt.serverId, prompt.vendor, stop = true)
			is VendorPrompt.ConfirmRestart -> dispatchPower(prompt.serverId, prompt.vendor, stop = false)
			else -> Unit
		}
	}

	fun confirmVendorUnbind() {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.ConfirmUnbind ?: return
		viewModelScope.launch {
			when (prompt.vendor) {
				VendorKind.BANDWAGON -> bandwagon.unbind(prompt.serverId)
				VendorKind.CLOUDCONE -> cloudcone.unbind(prompt.serverId)
				VendorKind.VIRTUALIZOR -> virtualizor.unbind(prompt.serverId)
			}
			_uiState.value = _uiState.value.copy(
				vendorPrompt = null,
				noticeMessageRes = R.string.bandwagon_unbound,
				noticeMessageArg = null
			)
		}
	}

	private fun loadVirtualizorInstances(
		serverId: Long,
		host: String,
		port: Int,
		apiKey: String,
		apiPass: String,
		certPinSha256: String?
	) {
		viewModelScope.launch {
			val listed = virtualizor.listInstances(host, port, apiKey, apiPass, certPinSha256)
			if (!listed.ok) {
				_uiState.value = _uiState.value.copy(
					noticeMessageRes = R.string.virtualizor_list_failed,
					noticeMessageArg = listed.message
				)
				return@launch
			}
			val taken = virtualizorLinkRows
				.filter { it.host == host.trim() && it.port == port }
				.map { it.vpsid }
				.toSet()
			val available = listed.instances.filter { it.id !in taken }
			_uiState.value = _uiState.value.copy(
				vendorPrompt = VendorPrompt.VirtualizorPick(
					serverId = serverId,
					instances = available,
					boundServerIds = virtualizorIds,
					host = host.trim(),
					port = port,
					apiKey = apiKey.trim(),
					apiPass = apiPass.trim(),
					certPinSha256 = certPinSha256
				)
			)
		}
	}

	private fun loadCloudconeInstances(serverId: Long, apiKey: String, hash: String) {
		viewModelScope.launch {
			val listed = cloudcone.listInstances(apiKey, hash)
			if (!listed.ok) {
				_uiState.value = _uiState.value.copy(
					noticeMessageRes = R.string.cloudcone_list_failed,
					noticeMessageArg = listed.message
				)
				return@launch
			}
			val taken = cloudconeLinkRows.map { it.instanceId }.toSet()
			val available = listed.instances.filter { it.id !in taken }
			_uiState.value = _uiState.value.copy(
				vendorPrompt = VendorPrompt.CloudconePick(
					serverId = serverId,
					instances = available,
					boundServerIds = cloudconeIds,
					apiKey = apiKey.trim(),
					hash = hash.trim()
				)
			)
		}
	}

	private fun startPower(serverId: Long, vendor: VendorKind?) {
		if (vendor == null) return
		dispatchPower(serverId, vendor, stop = null)
	}

	private fun dispatchPower(serverId: Long, vendor: VendorKind, stop: Boolean?) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(vendorPrompt = null)
			when (vendor) {
				VendorKind.BANDWAGON -> {
					val action = when (stop) {
						null -> BandwagonPowerAction.START
						true -> BandwagonPowerAction.STOP
						false -> BandwagonPowerAction.RESTART
					}
					showBandwagonPower(bandwagon.power(serverId, action, confirmed = true))
				}
				VendorKind.CLOUDCONE -> {
					val action = when (stop) {
						null -> CloudconePowerAction.BOOT
						true -> CloudconePowerAction.SHUTDOWN
						false -> CloudconePowerAction.REBOOT
					}
					showCloudconePower(cloudcone.power(serverId, action, confirmed = true))
				}
				VendorKind.VIRTUALIZOR -> {
					val action = when (stop) {
						null -> VirtualizorPowerAction.START
						true -> VirtualizorPowerAction.STOP
						false -> VirtualizorPowerAction.RESTART
					}
					showVirtualizorPower(virtualizor.power(serverId, action, confirmed = true))
				}
			}
		}
	}

	private fun queryServiceInfo(serverId: Long, vendor: VendorKind?) {
		when (vendor) {
			VendorKind.BANDWAGON -> viewModelScope.launch {
				when (val result = bandwagon.serviceInfo(serverId)) {
					is BandwagonServiceInfoResult.Known -> {
						_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.ServiceInfo(result.info))
					}
					BandwagonServiceInfoResult.NotBound -> notice(R.string.bandwagon_not_bound)
					is BandwagonServiceInfoResult.Failed -> noticeArg(R.string.bandwagon_service_info_failed, result.message)
				}
			}
			VendorKind.CLOUDCONE -> viewModelScope.launch {
				when (val result = cloudcone.instanceInfo(serverId)) {
					is CloudconeInfoResult.Known -> {
						_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.CloudconeInfo(result.info))
					}
					CloudconeInfoResult.NotBound -> notice(R.string.cloudcone_not_bound)
					is CloudconeInfoResult.Failed -> noticeArg(R.string.cloudcone_service_info_failed, result.message)
				}
			}
			else -> return
		}
	}

	private fun queryMigrateLocations(serverId: Long, vendor: VendorKind?) {
		if (vendor != VendorKind.BANDWAGON) return
		viewModelScope.launch {
			when (val result = bandwagon.migrateLocations(serverId)) {
				is BandwagonMigrateLocationsResult.Known -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = VendorPrompt.MigratePick(serverId, result.locations)
					)
				}
				BandwagonMigrateLocationsResult.NotBound -> notice(R.string.bandwagon_not_bound)
				is BandwagonMigrateLocationsResult.Failed -> noticeArg(R.string.bandwagon_migrate_failed, result.message)
			}
		}
	}

	fun pickMigrateLocation(location: BandwagonMigrateLocation) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.MigratePick ?: return
		_uiState.value = _uiState.value.copy(
			vendorPrompt = VendorPrompt.ConfirmMigrate(prompt.serverId, location)
		)
	}

	fun confirmMigrate() {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.ConfirmMigrate ?: return
		viewModelScope.launch {
			when (val result = bandwagon.migrateStart(prompt.serverId, prompt.location.id, confirmed = true)) {
				is BandwagonMigrateStartResult.Accepted -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = VendorPrompt.MigrateResult(prompt.serverId, result.start)
					)
				}
				BandwagonMigrateStartResult.NeedsConfirmation -> Unit
				BandwagonMigrateStartResult.NotBound -> {
					dismissVendorPrompt()
					notice(R.string.bandwagon_not_bound)
				}
				is BandwagonMigrateStartResult.Failed -> {
					dismissVendorPrompt()
					noticeArg(R.string.bandwagon_migrate_failed, result.message)
				}
			}
		}
	}

	fun confirmResetRoot() {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.ConfirmResetRoot ?: return
		viewModelScope.launch {
			when (val result = bandwagon.resetRootPassword(prompt.serverId, confirmed = true)) {
				is BandwagonResetRootResult.Accepted -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = VendorPrompt.ResetRootResult(prompt.serverId, result.password)
					)
				}
				BandwagonResetRootResult.NeedsConfirmation -> Unit
				BandwagonResetRootResult.NotBound -> {
					dismissVendorPrompt()
					notice(R.string.bandwagon_not_bound)
				}
				is BandwagonResetRootResult.Failed -> {
					dismissVendorPrompt()
					noticeArg(R.string.bandwagon_reset_failed, result.message)
				}
			}
		}
	}

	fun applyResetRootPassword() {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.ResetRootResult ?: return
		val server = _uiState.value.servers.find { it.id == prompt.serverId } ?: return
		viewModelScope.launch {
			serverManagementService.updateServer(
				serverId = server.id,
				name = server.name,
				host = server.host,
				port = server.port,
				username = server.username,
				authType = AuthType.PASSWORD,
				password = prompt.password,
				privateKey = null,
				keyPassphrase = null,
				groupId = server.groupId,
				proxyEnabled = server.proxyEnabled,
				proxyType = server.proxyType,
				proxyHost = server.proxyHost,
				proxyPort = server.proxyPort,
				proxyUsername = server.proxyUsername,
				proxyPassword = null,
				jumpHostId = server.jumpHostId
			).onSuccess {
				bandwagon.closeBoundSessions(server.id)
				dismissVendorPrompt()
				notice(R.string.bandwagon_reset_password_saved)
			}.onFailure { error ->
				noticeArg(R.string.bandwagon_reset_failed, error.message ?: "")
			}
		}
	}

	private fun queryUsage(serverId: Long, vendor: VendorKind?) {
		if (vendor != VendorKind.BANDWAGON) return
		viewModelScope.launch {
			when (val result = bandwagon.usageStats(serverId)) {
				is BandwagonUsageResult.Known -> {
					_uiState.value = _uiState.value.copy(
						vendorPrompt = VendorPrompt.UsageStats(result.stats, BandwagonUsageRange.DAY)
					)
				}
				BandwagonUsageResult.NotBound -> notice(R.string.bandwagon_not_bound)
				is BandwagonUsageResult.Failed -> noticeArg(R.string.bandwagon_usage_failed, result.message)
			}
		}
	}

	fun setUsageRange(range: BandwagonUsageRange) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.UsageStats ?: return
		_uiState.value = _uiState.value.copy(vendorPrompt = prompt.copy(range = range))
	}

	fun applyMigrateHost(ip: String) {
		val prompt = _uiState.value.vendorPrompt as? VendorPrompt.MigrateResult ?: return
		val server = _uiState.value.servers.find { it.id == prompt.serverId } ?: return
		viewModelScope.launch {
			serverManagementService.updateServer(server.copy(host = ip))
				.onSuccess {
					dismissVendorPrompt()
					noticeArg(R.string.bandwagon_migrate_host_updated, ip)
				}
				.onFailure { error ->
					noticeArg(R.string.bandwagon_migrate_failed, error.message ?: "")
				}
		}
	}

	private fun queryBandwidth(serverId: Long, vendor: VendorKind?) {
		if (vendor != VendorKind.VIRTUALIZOR) return
		viewModelScope.launch {
			when (val result = virtualizor.bandwidth(serverId)) {
				is VirtualizorTrafficResult.Known -> {
					_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Bandwidth(result.bandwidth))
				}
				VirtualizorTrafficResult.Unknown -> notice(R.string.virtualizor_bandwidth_unknown)
				VirtualizorTrafficResult.NoAccount -> notice(R.string.virtualizor_no_account)
				is VirtualizorTrafficResult.Failed -> noticeArg(R.string.virtualizor_bandwidth_failed, result.message)
			}
		}
	}

	private fun queryStatus(serverId: Long, vendor: VendorKind?) {
		if (vendor == null) return
		viewModelScope.launch {
			when (vendor) {
				VendorKind.BANDWAGON -> when (val result = bandwagon.status(serverId)) {
					is BandwagonStatusResult.Known -> {
						_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Status(result.veStatus))
					}
					BandwagonStatusResult.Unknown -> {
						_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.StatusUnknown)
					}
					is BandwagonStatusResult.Failed -> noticeArg(R.string.bandwagon_status_failed, result.message)
				}
				VendorKind.CLOUDCONE -> {
					when (val result = cloudcone.status(serverId)) {
						is CloudconeStatusResult.Known -> {
							_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Status(result.powerStatus))
						}
						CloudconeStatusResult.Unknown -> {
							_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.StatusUnknown)
						}
						is CloudconeStatusResult.Failed -> noticeArg(R.string.cloudcone_status_failed, result.message)
					}
				}
				VendorKind.VIRTUALIZOR -> {
					when (val result = virtualizor.status(serverId)) {
						is VirtualizorStatusResult.Known -> {
							_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.Status(result.powerStatus))
						}
						VirtualizorStatusResult.Unknown -> {
							_uiState.value = _uiState.value.copy(vendorPrompt = VendorPrompt.StatusUnknown)
						}
						is VirtualizorStatusResult.Failed -> noticeArg(R.string.virtualizor_status_failed, result.message)
					}
				}
			}
		}
	}

	private fun showBandwagonPower(result: BandwagonPowerResult) {
		when (result) {
			BandwagonPowerResult.Accepted -> notice(R.string.bandwagon_power_accepted)
			is BandwagonPowerResult.Failed -> noticeArg(R.string.bandwagon_power_failed, result.message)
			BandwagonPowerResult.NotBound -> notice(R.string.bandwagon_not_bound)
			BandwagonPowerResult.NeedsConfirmation -> Unit
		}
	}

	private fun showCloudconePower(result: CloudconePowerResult) {
		when (result) {
			CloudconePowerResult.Accepted -> notice(R.string.bandwagon_power_accepted)
			is CloudconePowerResult.Failed -> noticeArg(R.string.bandwagon_power_failed, result.message)
			CloudconePowerResult.NoAccount -> notice(R.string.cloudcone_no_account)
			CloudconePowerResult.NeedsConfirmation -> Unit
		}
	}

	private fun showVirtualizorPower(result: VirtualizorPowerResult) {
		when (result) {
			VirtualizorPowerResult.Accepted -> notice(R.string.bandwagon_power_accepted)
			is VirtualizorPowerResult.Failed -> noticeArg(R.string.bandwagon_power_failed, result.message)
			VirtualizorPowerResult.NoAccount -> notice(R.string.virtualizor_no_account)
			VirtualizorPowerResult.NeedsConfirmation -> Unit
		}
	}

	private fun notice(res: Int) {
		_uiState.value = _uiState.value.copy(noticeMessageRes = res, noticeMessageArg = null)
	}

	private fun noticeArg(res: Int, arg: String?) {
		_uiState.value = _uiState.value.copy(noticeMessageRes = res, noticeMessageArg = arg ?: "")
	}

	fun confirmDeleteServer() {
		val server = _uiState.value.serverToDelete ?: return
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, serverToDelete = null)
			val result = serverManagementService.deleteServer(server)
			result.fold(
				onSuccess = {
					_uiState.value = _uiState.value.copy(
						isLoading = false,
						error = null,
						noticeMessageRes = R.string.server_deleted
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
	 * 连接到服务器
	 */
	fun connectToServer(server: Server, onSuccess: (String) -> Unit) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				connectingServerId = server.id,
				error = null
			)
			val result = serverMonitoringService.connectToServer(server)
			result.fold(
				onSuccess = { session ->
					val sessionKey = "${server.id}_${System.currentTimeMillis()}"
					com.franzkafkayu.vcserver.utils.SessionManager.putSession(sessionKey, session)
					
					_uiState.value = _uiState.value.copy(
						connectingServerId = null
					)
					
					// 立即导航到监控页面，不等待系统信息更新
					onSuccess(sessionKey)
					
					// 在后台异步更新系统信息，不阻塞导航
					launch {
						updateServerSystemInfo(server, session)
					}
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						connectingServerId = null,
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 更新服务器的系统版本信息
	 */
	private suspend fun updateServerSystemInfo(server: Server, session: SshClientSession) {
		try {
			val osResult = serverMonitoringService.getOperatingSystem(session)
			osResult.fold(
				onSuccess = { systemInfo ->
					val systemVersion = "${systemInfo.osName} ${systemInfo.osVersion}".trim()
					if (systemVersion.isNotEmpty() && systemVersion != "Unknown Unknown") {
						val updatedServer = server.copy(
							systemVersion = systemVersion,
							updatedAt = System.currentTimeMillis()
						)
						serverManagementService.updateServer(updatedServer)
					}
				},
				onFailure = {
					// 获取系统信息失败，不影响连接流程
				}
			)
		} catch (e: Exception) {
			// 更新失败，不影响连接流程
		}
	}

	/**
	 * 清除错误
	 */
	fun clearError() {
		_uiState.value = _uiState.value.copy(error = null)
	}

	/**
	 * 更新服务器排序顺�?
	 */
	fun updateServerOrder(servers: List<Server>) {
		viewModelScope.launch {
			val result = serverManagementService.updateServerOrder(servers)
			result.fold(
				onSuccess = {
					// 排序更新成功，列表会自动刷新（通过 Flow�?
				},
				onFailure = { exception ->
					_uiState.value = _uiState.value.copy(
						error = exception.toAppError()
					)
				}
			)
		}
	}

	/**
	 * 进入选择模式
	 */
	fun enterSelectionMode(initialServerId: Long? = null) {
		_uiState.value = _uiState.value.copy(
			isSelectionMode = true,
			selectedServerIds = if (initialServerId != null) setOf(initialServerId) else emptySet()
		)
	}

	/**
	 * 退出选择模式
	 */
	fun exitSelectionMode() {
		_uiState.value = _uiState.value.copy(
			isSelectionMode = false,
			selectedServerIds = emptySet()
		)
	}

	/**
	 * 切换服务器选择状�?
	 */
	fun toggleServerSelection(serverId: Long) {
		val currentSelected = _uiState.value.selectedServerIds
		_uiState.value = _uiState.value.copy(
			selectedServerIds = if (currentSelected.contains(serverId)) {
				currentSelected - serverId
			} else {
				currentSelected + serverId
			}
		)
	}

	/**
	 * 全�?取消全�?
	 */
	fun toggleSelectAll() {
		val currentSelected = _uiState.value.selectedServerIds
		val allServerIds = _uiState.value.servers.map { it.id }.toSet()
		_uiState.value = _uiState.value.copy(
			selectedServerIds = if (currentSelected.size == allServerIds.size) {
				emptySet()
			} else {
				allServerIds
			}
		)
	}

	/**
	 * 显示批量删除确认对话�?
	 */
	fun showBatchDeleteConfirmDialog() {
		val selectedIds = _uiState.value.selectedServerIds
		val serversToDelete = _uiState.value.servers.filter { it.id in selectedIds }
		if (serversToDelete.isNotEmpty()) {
			_uiState.value = _uiState.value.copy(
				showBatchDeleteConfirm = true
			)
		}
	}

	/**
	 * 取消批量删除
	 */
	fun cancelBatchDelete() {
		_uiState.value = _uiState.value.copy(
			showBatchDeleteConfirm = false
		)
	}

	/**
	 * 确认批量删除选中的服务器
	 */
	fun confirmDeleteSelectedServers() {
		viewModelScope.launch {
			val selectedIds = _uiState.value.selectedServerIds
			val serversToDelete = _uiState.value.servers.filter { it.id in selectedIds }
			
			if (serversToDelete.isEmpty()) return@launch
			
			_uiState.value = _uiState.value.copy(
				isLoading = true,
				showBatchDeleteConfirm = false
			)
			
			var successCount = 0
			var failCount = 0
			
			serversToDelete.forEach { server ->
				val result = serverManagementService.deleteServer(server)
				if (result.isSuccess) {
					successCount++
				} else {
					failCount++
				}
			}
			
			_uiState.value = _uiState.value.copy(
				isLoading = false,
				isSelectionMode = false,
				selectedServerIds = emptySet(),
				error = if (failCount > 0) {
					AppError.NetworkError("成功删除 $successCount 个，失败 $failCount 个")
				} else {
					null
				}
			)
		}
	}

	/**
	 * 切换分组展开/折叠状态
	 */
	fun toggleGroupExpanded(groupId: Long) {
		val currentExpanded = _uiState.value.expandedGroupIds
		_uiState.value = _uiState.value.copy(
			expandedGroupIds = if (currentExpanded.contains(groupId)) {
				currentExpanded - groupId
			} else {
				currentExpanded + groupId
			}
		)
	}
}

/**
 * 服务器列表 UI 状态
 */
data class ServerListUiState(
	val servers: List<Server> = emptyList(),
	val groups: List<ServerGroup> = emptyList(),
	val groupedServers: List<Pair<ServerGroup, List<Server>>> = emptyList(),
	val ungroupedServers: List<Server> = emptyList(),
	val expandedGroupIds: Set<Long> = emptySet(), // 展开的分组ID集合（默认全部折叠）
	val isLoading: Boolean = true,
	val connectingServerId: Long? = null,
	val error: AppError? = null,
	val isSelectionMode: Boolean = false,
	val selectedServerIds: Set<Long> = emptySet(),
	val serverToDelete: Server? = null, // 待删除的服务器（用于显示确认对话框）
	val showBatchDeleteConfirm: Boolean = false, // 是否显示批量删除确认对话框
	val searchQuery: String = "", // 筛选关键词
	val isSearchActive: Boolean = false, // 搜索栏是否展开
	val searchSyntaxError: String? = null, // 查询语法错误提示
	val noticeMessageRes: Int? = null,
	val noticeMessageArg: String? = null,
	val serverVendors: Map<Long, VendorKind> = emptyMap(),
	val vendorPrompt: VendorPrompt? = null
)

sealed class VendorPrompt {
	data class Choose(val serverId: Long) : VendorPrompt()
	data class Bandwagon(val serverId: Long) : VendorPrompt()
	data class CloudconeSecrets(val serverId: Long) : VendorPrompt()
	data class CloudconePick(
		val serverId: Long,
		val instances: List<CloudconeInstance>,
		val boundServerIds: Set<Long>,
		val apiKey: String,
		val hash: String
	) : VendorPrompt()
	data class VirtualizorSecrets(val serverId: Long, val pendingPin: String? = null) : VendorPrompt()
	data class VirtualizorPick(
		val serverId: Long,
		val instances: List<VirtualizorVps>,
		val boundServerIds: Set<Long>,
		val host: String,
		val port: Int,
		val apiKey: String,
		val apiPass: String,
		val certPinSha256: String?
	) : VendorPrompt()
	data class ConfirmStop(val serverId: Long, val vendor: VendorKind) : VendorPrompt()
	data class ConfirmRestart(val serverId: Long, val vendor: VendorKind) : VendorPrompt()
	data class ConfirmUnbind(val serverId: Long, val vendor: VendorKind) : VendorPrompt()
	data class Status(val text: String) : VendorPrompt()
	data object StatusUnknown : VendorPrompt()
	data class Bandwidth(val bandwidth: VirtualizorBandwidth) : VendorPrompt()
	data class ServiceInfo(val info: BandwagonServiceInfo) : VendorPrompt()
	data class CloudconeInfo(val info: CloudconeInstanceInfo) : VendorPrompt()
	data class MigratePick(val serverId: Long, val locations: BandwagonMigrateLocations) : VendorPrompt()
	data class ConfirmMigrate(val serverId: Long, val location: BandwagonMigrateLocation) : VendorPrompt()
	data class MigrateResult(val serverId: Long, val start: BandwagonMigrateStart) : VendorPrompt()
	data class ConfirmResetRoot(val serverId: Long) : VendorPrompt()
	data class ResetRootResult(val serverId: Long, val password: String) : VendorPrompt()
	data class UsageStats(val stats: BandwagonUsageStats, val range: BandwagonUsageRange) : VendorPrompt()
}