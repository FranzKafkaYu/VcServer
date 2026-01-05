package com.franzkafkayu.vcserver.models

/**
 * 服务器状态信息
 */
data class ServerStatus(
	val cpu: CpuInfo,
	val memory: MemoryInfo,
	val disk: DiskInfo?,
	val uptime: String? = null,
	val systemInfo: SystemInfo? = null,
	val network: NetworkInfo? = null
)

/**
 * CPU 信息
 */
data class CpuInfo(
	val architecture: String? = null,  // CPU 架构，例如"x86_64", "aarch64"
	val model: String? = null,          // CPU 型号，例如"Intel(R) Core(TM) i7-8700K CPU @ 3.70GHz"
	val cores: Int,
	val usagePercent: Double
)

/**
 * 内存信息
 */
data class MemoryInfo(
	val total: String,      // 总内存，例如 "8GB"
	val used: String,       // 已用内存，例如"4GB"
	val available: String,  // 可用内存，例如"4GB"
	val usagePercent: Double // 使用率百分比
)

/**
 * 磁盘信息
 */
data class DiskInfo(
	val filesystem: String,  // 文件系统，例如"/dev/sda1"
	val mountPoint: String,  // 挂载点，例如 "/"
	val total: String,       // 总容量，例如 "100GB"
	val used: String,        // 已用容量，例如"50GB"
	val available: String,  // 可用容量，例如"50GB"
	val usagePercent: Double // 使用率百分比
)

/**
 * 系统信息
 */
data class SystemInfo(
	val osName: String,      // 操作系统名称，例如"Ubuntu"
	val osVersion: String,   // 操作系统版本，例如"22.04"
	val kernelVersion: String // 内核版本，例如"5.15.0"
)

/**
 * 网络信息
 */
data class NetworkInfo(
	val tcpConnections: Int,           // TCP 连接数
	val udpConnections: Int,          // UDP 连接数
	val interfaces: List<NetworkInterfaceInfo>  // 网卡列表
)

/**
 * 网卡信息
 */
data class NetworkInterfaceInfo(
	val name: String,                 // 网卡名称，例如 "eth0", "ens33"
	val totalRxBytes: Long,           // 总接收字节数（下行总量）
	val totalTxBytes: Long,           // 总发送字节数（上行总量）
	val currentRxRate: Double,        // 当前接收速率（字节/秒）
	val currentTxRate: Double         // 当前发送速率（字节/秒）
)

