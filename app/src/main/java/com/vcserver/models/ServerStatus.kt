package com.vcserver.models

/**
 * 服务器状态信息
 */
data class ServerStatus(
	val cpu: CpuInfo,
	val memory: MemoryInfo,
	val disk: DiskInfo?,
	val uptime: String? = null
)

/**
 * CPU 信息
 */
data class CpuInfo(
	val cores: Int,
	val usagePercent: Double
)

/**
 * 内存信息
 */
data class MemoryInfo(
	val total: String,      // 总内存，例如 "8GB"
	val used: String,       // 已用内存，例如 "4GB"
	val available: String,  // 可用内存，例如 "4GB"
	val usagePercent: Double // 使用率百分比
)

/**
 * 磁盘信息
 */
data class DiskInfo(
	val filesystem: String,  // 文件系统，例如 "/dev/sda1"
	val mountPoint: String,  // 挂载点，例如 "/"
	val total: String,       // 总容量，例如 "100GB"
	val used: String,        // 已用容量，例如 "50GB"
	val available: String,  // 可用容量，例如 "50GB"
	val usagePercent: Double // 使用率百分比
)

