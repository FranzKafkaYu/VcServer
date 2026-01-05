package com.franzkafkayu.vcserver.services

import android.util.Log
import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.DiskInfo
import com.franzkafkayu.vcserver.models.MemoryInfo
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerStatus
import com.franzkafkayu.vcserver.models.CpuInfo
import com.franzkafkayu.vcserver.models.SystemInfo
import com.franzkafkayu.vcserver.models.NetworkInfo
import com.franzkafkayu.vcserver.models.NetworkInterfaceInfo
import com.franzkafkayu.vcserver.utils.SecureStorage
import com.franzkafkayu.vcserver.utils.SshConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 服务器监控服务接口
 */
interface ServerMonitoringService {
	/**
	 * 连接到服务器
	 */
	suspend fun connectToServer(server: Server): Result<Session>

	/**
	 * 获取服务器状态
	 */
	suspend fun getServerStatus(session: Session): Result<ServerStatus>

	/**
	 * 断开连接
	 */
	fun disconnect(session: Session)

	/**
	 * 显式断开服务器连接（从连接池和 SessionManager 同时移除）
	 * 用于用户主动断开连接的情况
	 */
	fun disconnectFromServer(serverId: Long)
}

/**
 * 服务器监控服务实现
 */
class ServerMonitoringServiceImpl(
	private val sshAuthService: SshAuthenticationService,
	private val sshCommandService: SshCommandService,
	private val secureStorage: SecureStorage
) : ServerMonitoringService {
   companion object {
	  private const val TAG = "ServerMonitoringService"
   }
	

	override suspend fun connectToServer(server: Server): Result<Session> = withContext(Dispatchers.IO) {
		try {
			// 优先从连接池获取连接
			val pooledSession = SshConnectionPool.getConnection(server.id)
			if (pooledSession != null) {
				return@withContext Result.success(pooledSession)
			}

			// 连接池中没有有效连接，创建新连接
			// 解密敏感信息
			val password = server.encryptedPassword?.let { secureStorage.decryptPassword(it) }
			val privateKey = server.encryptedPrivateKey?.let { secureStorage.decryptPrivateKey(it) }

			val result = when (server.authType) {
				AuthType.PASSWORD -> {
					if (password == null) {
						return@withContext Result.failure(Exception("Password is required"))
					}
					sshAuthService.connectWithPassword(
						host = server.host,
						port = server.port,
						username = server.username,
						password = password
					)
				}
				AuthType.KEY -> {
					if (privateKey == null) {
						return@withContext Result.failure(Exception("Private key is required"))
					}
					sshAuthService.connectWithKey(
						host = server.host,
						port = server.port,
						username = server.username,
						privateKey = privateKey,
						passphrase = server.keyPassphrase
					)
				}
			}

			// 如果连接成功，将连接加入连接池
			result.fold(
				onSuccess = { session ->
					SshConnectionPool.putConnection(server.id, session)
					Result.success(session)
				},
				onFailure = { exception ->
					Result.failure(exception)
				}
			)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to connect to server: ${e.message}", e))
		}
	}

	override suspend fun getServerStatus(session: Session): Result<ServerStatus> = withContext(Dispatchers.IO) {
		try {
			val startTime = System.currentTimeMillis()
			coroutineScope {
				// 并行执行所有信息获取操作
				val cpuDeferred = async { 
					val t = System.currentTimeMillis()
					val r = getCpuInfo(session)
					Log.d(TAG, "getCpuInfo time cost: ${System.currentTimeMillis() - t}ms")
					r
				}
				val memoryDeferred = async { 
					val t = System.currentTimeMillis()
					val r = getMemoryInfo(session)
					Log.d(TAG, "getMemoryInfo time cost: ${System.currentTimeMillis() - t}ms")
					r
				}
				val diskDeferred = async { 
					val t = System.currentTimeMillis()
					val r = getDiskInfo(session)
					Log.d(TAG, "getDiskInfo time cost: ${System.currentTimeMillis() - t}ms")
					r
				}
				val uptimeDeferred = async { 
					val t = System.currentTimeMillis()
					val r = getUptime(session)
					Log.d(TAG, "getUptime time cost: ${System.currentTimeMillis() - t}ms")
					r
				}
				val systemInfoDeferred = async { 
					val t = System.currentTimeMillis()
					val r = getSystemInfo(session)
					Log.d(TAG, "getSystemInfo time cost: ${System.currentTimeMillis() - t}ms")
					r
				}

				// 等待必需的信息完成（CPU 和内存）
				val cpuResult = cpuDeferred.await()
				if (cpuResult.isFailure) {
					return@coroutineScope Result.failure(cpuResult.exceptionOrNull() ?: Exception("Failed to get CPU info"))
				}
				val cpu = cpuResult.getOrNull() ?: return@coroutineScope Result.failure(Exception("CPU info is null"))

				val memoryResult = memoryDeferred.await()
				if (memoryResult.isFailure) {
					return@coroutineScope Result.failure(memoryResult.exceptionOrNull() ?: Exception("Failed to get memory info"))
				}
				val memory = memoryResult.getOrNull() ?: return@coroutineScope Result.failure(Exception("Memory info is null"))

				// 等待磁盘信息（必需，但可为 null）
				val diskResult = diskDeferred.await()
				if (diskResult.isFailure) {
					return@coroutineScope Result.failure(diskResult.exceptionOrNull() ?: Exception("Failed to get disk info"))
				}
				val disk = diskResult.getOrNull()

				// 等待可选信息（如果失败则使用 null）
				val uptime = uptimeDeferred.await().getOrNull()
				val systemInfo = systemInfoDeferred.await().getOrNull()
				val network = async { getNetworkInfo(session) }.await().getOrNull()
				val duration = System.currentTimeMillis() - startTime
				Log.i(TAG, "getServerStatus total time cost: ${duration}ms")
				Result.success(ServerStatus(cpu = cpu, memory = memory, disk = disk, uptime = uptime, systemInfo = systemInfo, network = network))
			}
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get server status: ${e.message}", e))
		}
	}

	override fun disconnect(session: Session) {
		// 注意：这里只断开 Session，但不从连接池移除
		// 连接池会在连接无效时自动清理
		// 如果需要显式断开连接（用户主动断开），应该调用 disconnectFromServer(serverId)
		sshAuthService.closeSession(session)
	}

	override fun disconnectFromServer(serverId: Long) {
		SshConnectionPool.removeConnection(serverId)
	}

	/**
	 * 获取 CPU 信息（优化：简化命令，移除慢命令）
	 */
	private suspend fun getCpuInfo(session: Session): Result<CpuInfo> = coroutineScope {
		try {
			// 并行执行快速命令，移除慢命令（top 命令很慢，暂时跳过 CPU 使用率）
			val coresDeferred = async { sshCommandService.executeCommand(session, "nproc", timeout = 5000) }
			val archDeferred = async { sshCommandService.executeCommand(session, "uname -m", timeout = 5000) }
			val modelDeferred = async { 
				sshCommandService.executeCommand(
					session,
					"cat /proc/cpuinfo 2>/dev/null | grep -i 'model name' | head -1 | cut -d: -f2 | xargs",
					timeout = 5000
				)
			}

			// 等待必需的结果（CPU 核心数）
			val coresResult = coresDeferred.await()
			if (coresResult.isFailure) {
				return@coroutineScope Result.failure(coresResult.exceptionOrNull() ?: Exception("Failed to get CPU cores"))
			}
			val cores = coresResult.getOrNull()?.toIntOrNull() ?: 1

			// 等待其他结果
			val archResult = archDeferred.await()
			val architecture = if (archResult.isSuccess) {
				archResult.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
			} else {
				null
			}

			val modelResult = modelDeferred.await()
			val model = if (modelResult.isSuccess) {
				modelResult.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
			} else {
				null
			}

			// CPU 使用率：暂时返回 0.0，因为 top 命令很慢且需要两次采样才能准确计算
			// 如果需要，可以在后台异步更新
			val usagePercent = 0.0

			Result.success(CpuInfo(
				architecture = architecture,
				model = model,
				cores = cores,
				usagePercent = usagePercent
			))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse CPU info: ${e.message}", e))
		}
	}

	/**
	 * 从 /proc/stat 解析 CPU 使用率
	 */
	private fun parseCpuUsageFromStat(statLine: String): Double {
		// 简化实现，返回 0.0
		// 实际实现需要计算两次采样之间的差值
		return 0.0
	}

	/**
	 * 获取内存信息（优化：减少超时时间）
	 */
	private suspend fun getMemoryInfo(session: Session): Result<MemoryInfo> {
		return try {
			val result = sshCommandService.executeCommand(session, "free -h", timeout = 5000)
			if (result.isFailure) {
				return Result.failure(result.exceptionOrNull() ?: Exception("Failed to execute free command"))
			}

			val output = result.getOrNull() ?: return Result.failure(Exception("Empty output"))
			val lines = output.lines()
			if (lines.size < 2) {
				return Result.failure(Exception("Invalid free command output"))
			}

			// 解析第二行（Mem 行）
			val memLine = lines[1]
			val parts = memLine.split(Regex("\\s+"))
			if (parts.size < 4) {
				return Result.failure(Exception("Invalid memory line format"))
			}

			val total = parts[1]
			val used = parts[2]
			val available = parts[6] // free 命令的 available 列

			// 计算使用率
			val totalBytes = parseSizeToBytes(total)
			val usedBytes = parseSizeToBytes(used)
			val usagePercent = if (totalBytes > 0) {
				(usedBytes.toDouble() / totalBytes.toDouble()) * 100.0
			} else {
				0.0
			}

			Result.success(
				MemoryInfo(
					total = total,
					used = used,
					available = available,
					usagePercent = usagePercent
				)
			)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse memory info: ${e.message}", e))
		}
	}

	/**
	 * 获取磁盘信息（汇总所有分区，优化：减少超时时间）
	 */
	private suspend fun getDiskInfo(session: Session): Result<DiskInfo> {
		return try {
			val result = sshCommandService.executeCommand(session, "df -h", timeout = 5000)
			if (result.isFailure) {
				return Result.failure(result.exceptionOrNull() ?: Exception("Failed to execute df command"))
			}

			val output = result.getOrNull() ?: return Result.failure(Exception("Empty output"))
			val lines = output.lines()
			if (lines.size < 2) {
				return Result.failure(Exception("Invalid df command output"))
			}

			var totalBytes = 0L
			var usedBytes = 0L
			var availableBytes = 0L

			// 跳过第一行（标题行），汇总所有真实磁盘分区
			for (i in 1 until lines.size) {
				val line = lines[i].trim()
				if (line.isEmpty()) continue

				val parts = line.split(Regex("\\s+"))
				if (parts.size < 6) continue

				val filesystem = parts[0]
				val mountPoint = parts[5]

				// 只统计真实物理磁盘设备，排除虚拟文件系统
				val isRealDisk = filesystem.startsWith("/dev/sd") || 
					filesystem.startsWith("/dev/hd") || 
					filesystem.startsWith("/dev/nvme") ||
					filesystem.startsWith("/dev/vd") ||
					filesystem.startsWith("/dev/xvd")

				// 排除 loop 设备、tmpfs、udev 等虚拟文件系统
				val isVirtual = filesystem.startsWith("tmpfs") || 
					filesystem.startsWith("udev") || 
					filesystem.startsWith("devtmpfs") || 
					filesystem.startsWith("sysfs") || 
					filesystem.startsWith("proc") ||
					filesystem.startsWith("/dev/loop") ||
					mountPoint.startsWith("/snap/") ||
					mountPoint == "/dev" ||
					mountPoint == "/run" ||
					mountPoint == "/sys/fs/cgroup" ||
					mountPoint.startsWith("/run/user/") ||
					mountPoint == "/run/lock" ||
					mountPoint == "/dev/shm"

				// 只统计真实磁盘且不是虚拟文件系统
				if (!isRealDisk || isVirtual) {
					continue
				}

				val total = parts[1]
				val used = parts[2]
				val available = parts[3]

				val totalBytesForPartition = parseSizeToBytes(total)
				val usedBytesForPartition = parseSizeToBytes(used)
				val availableBytesForPartition = parseSizeToBytes(available)

				// 只累加真实磁盘分区
				totalBytes += totalBytesForPartition
				usedBytes += usedBytesForPartition
				availableBytes += availableBytesForPartition
			}

			if (totalBytes == 0L) {
				return Result.failure(Exception("No valid disk partitions found"))
			}

			// 计算总的使用率
			val usagePercent = if (totalBytes > 0) {
				(usedBytes.toDouble() / totalBytes.toDouble()) * 100.0
			} else {
				0.0
			}

			// 格式化容量信息
			val totalFormatted = formatBytes(totalBytes)
			val usedFormatted = formatBytes(usedBytes)
			val availableFormatted = formatBytes(availableBytes)

			Result.success(
				DiskInfo(
					filesystem = "总计",
					mountPoint = "",
					total = totalFormatted,
					used = usedFormatted,
					available = availableFormatted,
					usagePercent = usagePercent
				)
			)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse disk info: ${e.message}", e))
		}
	}

	/**
	 * 格式化字节数为易读格式
	 */
	private fun formatBytes(bytes: Long): String {
		return when {
			bytes >= 1024L * 1024 * 1024 * 1024 -> {
				String.format("%.2f TB", bytes.toDouble() / (1024.0 * 1024 * 1024 * 1024))
			}
			bytes >= 1024L * 1024 * 1024 -> {
				String.format("%.2f GB", bytes.toDouble() / (1024.0 * 1024 * 1024))
			}
			bytes >= 1024L * 1024 -> {
				String.format("%.2f MB", bytes.toDouble() / (1024.0 * 1024))
			}
			bytes >= 1024L -> {
				String.format("%.2f KB", bytes.toDouble() / 1024.0)
			}
			else -> "$bytes B"
		}
	}

	/**
	 * 获取系统启动时长（优化：减少超时时间）
	 */
	private suspend fun getUptime(session: Session): Result<String> {
		return try {
			val result = sshCommandService.executeCommand(session, "uptime -p", timeout = 5000)
			val uptimeText = if (result.isSuccess) {
				result.getOrNull() ?: ""
			} else {
				// 备用方法
				val result2 = sshCommandService.executeCommand(session, "uptime", timeout = 5000)
				result2.getOrNull() ?: ""
			}
			
			// 格式化 uptime 输出
			val formatted = formatUptime(uptimeText)
			Result.success(formatted)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get uptime: ${e.message}", e))
		}
	}

	/**
	 * 格式化 uptime 输出为 "xxx days,xx hours,xxx minutes" 格式
	 * 输入示例: "up 2 days, 3 hours, 45 minutes" 或 "up 1 hour, 30 minutes"
	 */
	private fun formatUptime(uptimeText: String): String {
		if (uptimeText.isBlank()) return ""
		
		// 去掉 "up" 单词（不区分大小写）
		var text = uptimeText.trim()
		text = text.replace(Regex("^up\\s+", RegexOption.IGNORE_CASE), "")
		
		// 解析各个时间单位
		var totalDays = 0
		var hours = 0
		var minutes = 0
		
		// 匹配 years（不区分大小写），1 year = 365 days
		val yearsMatch = Regex("(\\d+)\\s+year", RegexOption.IGNORE_CASE).find(text)
		yearsMatch?.let { 
			val years = it.groupValues[1].toIntOrNull() ?: 0
			totalDays += years * 365
		}
		
		// 匹配 weeks（不区分大小写），1 week = 7 days
		val weeksMatch = Regex("(\\d+)\\s+week", RegexOption.IGNORE_CASE).find(text)
		weeksMatch?.let { 
			val weeks = it.groupValues[1].toIntOrNull() ?: 0
			totalDays += weeks * 7
		}
		
		// 匹配 days（不区分大小写）
		val daysMatch = Regex("(\\d+)\\s+day", RegexOption.IGNORE_CASE).find(text)
		daysMatch?.let { 
			val days = it.groupValues[1].toIntOrNull() ?: 0
			totalDays += days
		}
		
		// 匹配 hours（不区分大小写）
		val hoursMatch = Regex("(\\d+)\\s+hour", RegexOption.IGNORE_CASE).find(text)
		hoursMatch?.let { hours = it.groupValues[1].toIntOrNull() ?: 0 }
		
		// 匹配 minutes（不区分大小写）
		val minutesMatch = Regex("(\\d+)\\s+minute", RegexOption.IGNORE_CASE).find(text)
		minutesMatch?.let { minutes = it.groupValues[1].toIntOrNull() ?: 0 }
		
		// 如果没有找到任何时间单位，尝试从 /proc/uptime 解析（备用方案）
		if (totalDays == 0 && hours == 0 && minutes == 0) {
			// 尝试解析秒数（如果输出是秒数格式）
			val secondsMatch = Regex("(\\d+)").find(text)
			secondsMatch?.let {
				val totalSeconds = it.groupValues[1].toLongOrNull() ?: 0
				totalDays = (totalSeconds / 86400).toInt()
				hours = ((totalSeconds % 86400) / 3600).toInt()
				minutes = ((totalSeconds % 3600) / 60).toInt()
			}
		}
		
		// 构建格式化字符串，按照用户要求的格式：xxx days,xx hours,xxx minutes
		val parts = mutableListOf<String>()
		if (totalDays > 0) {
			parts.add("$totalDays days")
		}
		if (hours > 0) {
			parts.add("$hours hours")
		}
		if (minutes > 0) {
			parts.add("$minutes minutes")
		}
		
		// 如果所有值都是0，返回默认值
		if (parts.isEmpty()) {
			return "0 minutes"
		}
		
		return parts.joinToString(", ")
	}

	/**
	 * 获取系统信息和内核版本（优化：并行执行命令，减少超时时间）
	 */
	private suspend fun getSystemInfo(session: Session): Result<SystemInfo> = coroutineScope {
		try {
			// 并行执行命令，使用较短的超时时间
			val kernelDeferred = async { sshCommandService.executeCommand(session, "uname -r", timeout = 5000) }
			val osReleaseDeferred = async { sshCommandService.executeCommand(session, "cat /etc/os-release", timeout = 5000) }

			// 等待结果
			val kernelResult = kernelDeferred.await()
			val kernelVersion = kernelResult.getOrNull()?.trim() ?: "Unknown"

			val osResult = osReleaseDeferred.await()
			val osRelease = osResult.getOrNull() ?: ""

			var osName = "Unknown"
			var osVersion = "Unknown"

			if (osRelease.isNotEmpty()) {
				// 解析 /etc/os-release 文件
				val lines = osRelease.lines()
				for (line in lines) {
					when {
						line.startsWith("PRETTY_NAME=") -> {
							// 提取操作系统名称和版本
							val value = line.substringAfter("=").trim('"', '\'')
							// 尝试分离名称和版本（例如 "Ubuntu 22.04.3 LTS"）
							val parts = value.split(" ", limit = 2)
							if (parts.size >= 1) {
								osName = parts[0]
								if (parts.size >= 2) {
									// 提取版本号（例如从 "22.04.3 LTS" 中提取 "22.04"）
									val versionMatch = Regex("(\\d+\\.\\d+)").find(parts[1])
									versionMatch?.let {
										osVersion = it.groupValues[1]
									} ?: run {
										osVersion = parts[1].split(" ")[0]
									}
								}
							}
						}
						line.startsWith("NAME=") && osName == "Unknown" -> {
							osName = line.substringAfter("=").trim('"', '\'')
						}
						line.startsWith("VERSION_ID=") && osVersion == "Unknown" -> {
							osVersion = line.substringAfter("=").trim('"', '\'')
						}
					}
				}
			} else {
				// 备用方法：使用 uname -a（如果需要）
				val unameResult = sshCommandService.executeCommand(session, "uname -a", timeout = 5000)
				val unameOutput = unameResult.getOrNull() ?: ""
				if (unameOutput.isNotEmpty()) {
					// 从 uname -a 输出中提取信息
					// 格式通常为: Linux hostname kernel-version #version date time arch
					val parts = unameOutput.split(" ")
					if (parts.size >= 3) {
						osName = parts[0] // "Linux"
						// 尝试从其他部分提取更多信息
					}
				}
			}

			Result.success(SystemInfo(
				osName = osName,
				osVersion = osVersion,
				kernelVersion = kernelVersion
			))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get system info: ${e.message}", e))
		}
	}

	/**
	 * 获取网络信息
	 */
	private suspend fun getNetworkInfo(session: Session): Result<NetworkInfo> = coroutineScope {
		try {
			// 并行获取连接数
			val tcpDeferred = async { getTcpConnections(session) }
			val udpDeferred = async { getUdpConnections(session) }
			val interfacesDeferred = async { getNetworkInterfacesWithTraffic(session) }

			// 等待连接数结果
			val tcpConnections = tcpDeferred.await().getOrNull() ?: 0
			val udpConnections = udpDeferred.await().getOrNull() ?: 0
			val interfaces = interfacesDeferred.await().getOrNull() ?: emptyList()

			Result.success(NetworkInfo(
				tcpConnections = tcpConnections,
				udpConnections = udpConnections,
				interfaces = interfaces
			))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get network info: ${e.message}", e))
		}
	}

	/**
	 * 获取 TCP 连接数
	 */
	private suspend fun getTcpConnections(session: Session): Result<Int> {
		return try {
			// 优先使用 ss 命令，如果失败则使用 netstat
			val result = sshCommandService.executeCommand(session, "ss -s 2>/dev/null | grep -i TCP | head -1 | awk '{print \$2}'", timeout = 5000)
			if (result.isSuccess) {
				val count = result.getOrNull()?.trim()?.toIntOrNull()
				if (count != null) {
					return Result.success(count)
				}
			}
			// 备用方法：使用 netstat
			val netstatResult = sshCommandService.executeCommand(session, "netstat -an 2>/dev/null | grep -i tcp | wc -l", timeout = 5000)
			val count = netstatResult.getOrNull()?.trim()?.toIntOrNull() ?: 0
			Result.success(count)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get TCP connections: ${e.message}", e))
		}
	}

	/**
	 * 获取 UDP 连接数
	 */
	private suspend fun getUdpConnections(session: Session): Result<Int> {
		return try {
			// 优先使用 ss 命令，如果失败则使用 netstat
			val result = sshCommandService.executeCommand(session, "ss -s 2>/dev/null | grep -i UDP | head -1 | awk '{print \$2}'", timeout = 5000)
			if (result.isSuccess) {
				val count = result.getOrNull()?.trim()?.toIntOrNull()
				if (count != null) {
					return Result.success(count)
				}
			}
			// 备用方法：使用 netstat
			val netstatResult = sshCommandService.executeCommand(session, "netstat -an 2>/dev/null | grep -i udp | wc -l", timeout = 5000)
			val count = netstatResult.getOrNull()?.trim()?.toIntOrNull() ?: 0
			Result.success(count)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get UDP connections: ${e.message}", e))
		}
	}

	/**
	 * 获取网卡列表和流量统计（包含速率计算）
	 */
	private suspend fun getNetworkInterfacesWithTraffic(session: Session): Result<List<NetworkInterfaceInfo>> {
		return try {
			// 第一次采样
			val firstSample = getNetworkInterfacesTraffic(session)
			if (firstSample.isFailure) {
				return Result.failure(firstSample.exceptionOrNull() ?: Exception("Failed to get first traffic sample"))
			}

			// 等待1秒
			kotlinx.coroutines.delay(1000)

			// 第二次采样
			val secondSample = getNetworkInterfacesTraffic(session)
			if (secondSample.isFailure) {
				// 如果第二次采样失败，只返回第一次的数据（速率为0）
				val firstData = firstSample.getOrNull() ?: return Result.success(emptyList())
				return Result.success(firstData.map { 
					it.copy(currentRxRate = 0.0, currentTxRate = 0.0)
				})
			}

			val firstData = firstSample.getOrNull() ?: return Result.success(emptyList())
			val secondData = secondSample.getOrNull() ?: return Result.success(emptyList())

			// 计算速率（字节/秒）
			val interfacesWithRate = firstData.map { first ->
				val second = secondData.find { it.name == first.name }
				if (second != null) {
					val rxRate = (second.totalRxBytes - first.totalRxBytes).toDouble()
					val txRate = (second.totalTxBytes - first.totalTxBytes).toDouble()
					first.copy(
						totalRxBytes = second.totalRxBytes,
						totalTxBytes = second.totalTxBytes,
						currentRxRate = rxRate.coerceAtLeast(0.0),
						currentTxRate = txRate.coerceAtLeast(0.0)
					)
				} else {
					first.copy(currentRxRate = 0.0, currentTxRate = 0.0)
				}
			}

			Result.success(interfacesWithRate)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get network interfaces with traffic: ${e.message}", e))
		}
	}

	/**
	 * 获取网卡流量统计（单次采样）
	 */
	private suspend fun getNetworkInterfacesTraffic(session: Session): Result<List<NetworkInterfaceInfo>> {
		return try {
			val result = sshCommandService.executeCommand(session, "cat /proc/net/dev", timeout = 5000)
			if (result.isFailure) {
				return Result.failure(result.exceptionOrNull() ?: Exception("Failed to execute cat /proc/net/dev"))
			}

			val output = result.getOrNull() ?: return Result.failure(Exception("Empty output"))
			val lines = output.lines()

			val interfaces = mutableListOf<NetworkInterfaceInfo>()

			// 跳过前两行（标题行）
			for (i in 2 until lines.size) {
				val line = lines[i].trim()
				if (line.isEmpty()) continue

				// 解析格式：interface_name: rx_bytes rx_packets ... tx_bytes tx_packets ...
				val colonIndex = line.indexOf(':')
				if (colonIndex < 0) continue

				val interfaceName = line.substring(0, colonIndex).trim()
				// 跳过 loopback 和虚拟接口
				if (interfaceName.startsWith("lo") || interfaceName.contains("docker") || interfaceName.contains("veth")) {
					continue
				}

				val dataPart = line.substring(colonIndex + 1).trim()
				val parts = dataPart.split(Regex("\\s+"))
				if (parts.size < 16) continue

				// /proc/net/dev 格式：
				// interface: rx_bytes rx_packets rx_errs rx_drop rx_fifo rx_frame rx_compressed rx_multicast
				//            tx_bytes tx_packets tx_errs tx_drop tx_fifo tx_colls tx_carrier tx_compressed
				val rxBytes = parts[0].toLongOrNull() ?: 0L
				val txBytes = parts[8].toLongOrNull() ?: 0L

				// 只添加有流量的网卡
				if (rxBytes > 0 || txBytes > 0) {
					interfaces.add(NetworkInterfaceInfo(
						name = interfaceName,
						totalRxBytes = rxBytes,
						totalTxBytes = txBytes,
						currentRxRate = 0.0, // 将在计算时填充
						currentTxRate = 0.0  // 将在计算时填充
					))
				}
			}

			Result.success(interfaces)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse network interfaces: ${e.message}", e))
		}
	}

	/**
	 * 将大小字符串转换为字节数（用于计算百分比）
	 */
	private fun parseSizeToBytes(size: String): Long {
		val sizeUpper = size.uppercase().trim()
		val number = sizeUpper.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
		return when {
			// 二进制单位（TiB, GiB, MiB, KiB等）- 先检查长单位
			sizeUpper.endsWith("TIB") || sizeUpper.endsWith("TB") -> (number * 1024 * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("GIB") || sizeUpper.endsWith("GB") || sizeUpper.endsWith("GI") -> (number * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("MIB") || sizeUpper.endsWith("MB") || sizeUpper.endsWith("MI") -> (number * 1024 * 1024).toLong()
			sizeUpper.endsWith("KIB") || sizeUpper.endsWith("KB") || sizeUpper.endsWith("KI") -> (number * 1024).toLong()
			// 短单位（T, G, M, K）- 最后检查，避免误匹配
			sizeUpper.endsWith("T") -> (number * 1024 * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("G") -> (number * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("M") -> (number * 1024 * 1024).toLong()
			sizeUpper.endsWith("K") -> (number * 1024).toLong()
			sizeUpper.endsWith("B") -> number.toLong()
			else -> {
				// 如果没有单位，假设是字节
				if (number > 0) number.toLong() else 0L
			}
		}
	}
}

