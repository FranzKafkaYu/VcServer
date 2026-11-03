package com.franzkafkayu.vcserver.ui.viewmodels

import com.franzkafkayu.vcserver.services.VendorKind

/**
 * 经厂商 API 完成的指令类型
 */
enum class VendorOperationKind {
	START,
	STOP,
	RESTART,
	STATUS,
	SERVICE_INFO,
	MIGRATE_LOCATIONS,
	MIGRATE_START,
	RESET_ROOT,
	USAGE,
	BANDWIDTH,
	LIST_INSTANCES,
	BIND
}

/**
 * 一次正在执行的厂商指令
 *
 * @property id 本次执行的唯一标识，用于判断结束的是不是当前显示的那一条
 * @property serverId 指令作用的服务器
 * @property vendor 指令归属的厂商
 * @property kind 指令类型
 */
data class VendorOperation(
	val id: Long,
	val serverId: Long,
	val vendor: VendorKind?,
	val kind: VendorOperationKind
)

/**
 * 在途厂商指令登记
 *
 * 以（服务器，指令类型）为键：同一台服务器的同一项指令执行中重复触发会被忽略，
 * 不同指令互不影响。仅在主线程使用（ViewModel 的状态变更都在主线程）。
 */
class VendorOperationTracker {
	private var nextId = 0L
	private val running = LinkedHashMap<OperationKey, VendorOperation>()

	/**
	 * 登记一次新的执行。同一（服务器，指令类型）已在执行时返回 null。
	 */
	fun begin(serverId: Long, vendor: VendorKind?, kind: VendorOperationKind): VendorOperation? {
		val key = OperationKey(serverId, kind)
		if (running.containsKey(key)) return null
		val operation = VendorOperation(
			id = ++nextId,
			serverId = serverId,
			vendor = vendor,
			kind = kind
		)
		running[key] = operation
		return operation
	}

	/**
	 * 结束一次执行。只有仍登记着的执行才会被移除，过期标识不会影响后续登记。
	 *
	 * @return 是否移除了登记
	 */
	fun finish(id: Long): Boolean {
		val key = running.entries.firstOrNull { it.value.id == id }?.key ?: return false
		running.remove(key)
		return true
	}

	/**
	 * 判断某项指令是否仍在执行
	 */
	fun isRunning(serverId: Long, kind: VendorOperationKind): Boolean {
		return running.containsKey(OperationKey(serverId, kind))
	}

	private data class OperationKey(
		val serverId: Long,
		val kind: VendorOperationKind
	)
}
