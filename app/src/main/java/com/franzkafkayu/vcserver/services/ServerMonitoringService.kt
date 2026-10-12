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
import java.util.Locale

/**
 * 服务器监控服务接口
 */
interface ServerMonitoringService {
	/**
	 * 连接到服务器
	 */
	suspend fun connectToServer(server: Server): Result<Session>

	/**
	 * 获取服务器状态。CPU、内存、磁盘与网络信息在同一次结果中返回。
	 */
	suspend fun getServerStatus(session: Session): Result<ServerStatus>

	/**
	 * 只读取操作系统标识，供列表后台更新系统版本。
	 */
	suspend fun getOperatingSystem(session: Session): Result<SystemInfo>

	/**
	 * 获取网络连接数与速率。包含约 1 秒的流量采样，不得放在首次状态展示的关键路径上。
	 */
	suspend fun getNetworkInfo(session: Session): Result<NetworkInfo>

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
		internal val CPU_STAT_SAMPLE_COMMAND = """
			head -1 /proc/stat 2>/dev/null || true
			sleep 1
			head -1 /proc/stat 2>/dev/null || true
		""".trimIndent()
		internal val CORE_SNAPSHOT_COMMAND = buildString {
			appendLine("echo '---VC_CPU_STAT---'")
			appendLine(CPU_STAT_SAMPLE_COMMAND)
			appendLine("echo '---VC_CPU_CORES---'")
			appendLine("nproc 2>/dev/null || echo 1")
			appendLine("echo '---VC_CPU_ARCH---'")
			appendLine("uname -m 2>/dev/null")
			appendLine("echo '---VC_CPU_MODEL---'")
			appendLine("grep -i 'model name' /proc/cpuinfo 2>/dev/null | head -1 | cut -d: -f2 | xargs")
			appendLine("echo '---VC_MEM---'")
			appendLine("free -b 2>/dev/null || free -h 2>/dev/null")
			appendLine("echo '---VC_DISK---'")
			appendLine("df -h 2>/dev/null")
			appendLine("echo '---VC_UPTIME---'")
			appendLine("uptime -p 2>/dev/null || uptime 2>/dev/null")
			appendLine("echo '---VC_KERNEL---'")
			appendLine("uname -r 2>/dev/null")
			appendLine("echo '---VC_OS---'")
			appendLine("cat /etc/os-release 2>/dev/null")
			appendLine("echo '---VC_END---'")
		}.trimEnd()
		internal val OS_ONLY_COMMAND = """
			echo '---VC_KERNEL---'
			uname -r 2>/dev/null
			echo '---VC_OS---'
			cat /etc/os-release 2>/dev/null
			echo '---VC_END---'
		""".trimIndent()
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
				val coreDeferred = async { loadCoreStatus(session) }
				val networkDeferred = async { getNetworkInfo(session).getOrNull() }
				val core = coreDeferred.await()
				if (core.isFailure) {
					return@coroutineScope Result.failure(
						core.exceptionOrNull() ?: Exception("Failed to get server status")
					)
				}
				val status = core.getOrThrow().copy(network = networkDeferred.await())
				Log.i(TAG, "getServerStatus total time cost: ${System.currentTimeMillis() - startTime}ms")
				Result.success(status)
			}
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get server status: ${e.message}", e))
		}
	}

	private suspend fun loadCoreStatus(session: Session): Result<ServerStatus> {
		val scriptOutput = sshCommandService.executeCommand(
			session,
			CORE_SNAPSHOT_COMMAND,
			timeout = 12000
		).getOrNull()
		val snapshot = scriptOutput?.let { parseCoreSnapshot(it) }
		if (snapshot != null) {
			Log.d(TAG, "getServerStatus core via single exec")
			return Result.success(snapshot)
		}
		Log.d(TAG, "getServerStatus core via fallback commands")
		return loadCoreStatusFallback(session)
	}

	override suspend fun getOperatingSystem(session: Session): Result<SystemInfo> = withContext(Dispatchers.IO) {
		try {
			val output = sshCommandService.executeCommand(session, OS_ONLY_COMMAND, timeout = 5000).getOrNull()
			if (output != null && output.contains("---VC_END---")) {
				return@withContext Result.success(
					parseSystemInfo(snapshotSection(output, "KERNEL"), snapshotSection(output, "OS"))
				)
			}
			getSystemInfo(session)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get operating system: ${e.message}", e))
		}
	}

	private suspend fun loadCoreStatusFallback(session: Session): Result<ServerStatus> = coroutineScope {
		val cpuDeferred = async { getCpuInfo(session) }
		val memoryDeferred = async { getMemoryInfo(session) }
		val diskDeferred = async { getDiskInfo(session) }
		val uptimeDeferred = async { getUptime(session) }
		val systemInfoDeferred = async { getSystemInfo(session) }

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

		val disk = diskDeferred.await().getOrNull()
		val uptime = uptimeDeferred.await().getOrNull()
		val systemInfo = systemInfoDeferred.await().getOrNull()
		Result.success(
			ServerStatus(
				cpu = cpu,
				memory = memory,
				disk = disk,
				uptime = uptime,
				systemInfo = systemInfo,
				network = null
			)
		)
	}

	internal fun parseCoreSnapshot(output: String): ServerStatus? {
		if (!output.contains("---VC_END---") || !output.contains("---VC_CPU_CORES---")) {
			return null
		}
		val coreCpu = coreCpuFromSnapshot(output) ?: return null
		val arch = snapshotSection(output, "CPU_ARCH").lineSequence().firstOrNull { it.isNotBlank() }?.trim()
		val model = snapshotSection(output, "CPU_MODEL").lineSequence().firstOrNull { it.isNotBlank() }?.trim()
			?.takeIf { it.isNotEmpty() }
		val memory = parseMemoryOutput(snapshotSection(output, "MEM")).getOrNull() ?: return null
		val disk = parseDiskOutput(snapshotSection(output, "DISK")).getOrNull()
		val uptimeText = snapshotSection(output, "UPTIME")
		val uptime = formatUptime(uptimeText).takeIf { it.isNotEmpty() && uptimeText.isNotBlank() }
		val systemInfo = parseSystemInfo(snapshotSection(output, "KERNEL"), snapshotSection(output, "OS"))
		return ServerStatus(
			cpu = CpuInfo(
				architecture = arch,
				model = model,
				cores = coreCpu.first,
				usagePercent = coreCpu.second
			),
			memory = memory,
			disk = disk,
			uptime = uptime,
			systemInfo = systemInfo,
			network = null
		)
	}

	private fun snapshotSection(output: String, name: String): String = snapshotSectionOf(output, name)

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
	 * 获取 CPU 信息。使用率与快照共用两次 /proc/stat 采样。
	 */
	private suspend fun getCpuInfo(session: Session): Result<CpuInfo> = coroutineScope {
		try {
			val coresDeferred = async { sshCommandService.executeCommand(session, "nproc", timeout = 5000) }
			val archDeferred = async { sshCommandService.executeCommand(session, "uname -m", timeout = 5000) }
			val modelDeferred = async {
				sshCommandService.executeCommand(
					session,
					"cat /proc/cpuinfo 2>/dev/null | grep -i 'model name' | head -1 | cut -d: -f2 | xargs",
					timeout = 5000
				)
			}
			val usageDeferred = async {
				sshCommandService.executeCommand(session, CPU_STAT_SAMPLE_COMMAND, timeout = 8000)
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

			val usagePercent = cpuUsagePercent(usageDeferred.await().getOrNull().orEmpty())

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
	 * 获取内存信息（优化：减少超时时间）
	 */
	private suspend fun getMemoryInfo(session: Session): Result<MemoryInfo> {
		return try {
			val bytesResult = sshCommandService.executeCommand(session, "free -b", timeout = 5000)
			val output = if (bytesResult.isSuccess) {
				bytesResult.getOrNull()
			} else {
				null
			} ?: sshCommandService.executeCommand(session, "free -h", timeout = 5000).getOrNull()
				?: return Result.failure(Exception("Failed to execute free command"))
			parseMemoryOutput(output)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse memory info: ${e.message}", e))
		}
	}

	private fun parseMemoryOutput(output: String): Result<MemoryInfo> {
		return try {
			val lines = output.lines().filter { it.isNotBlank() }
			if (lines.size < 2) {
				return Result.failure(Exception("Invalid free command output"))
			}

			val memLine = lines.firstOrNull { it.trim().startsWith("Mem", ignoreCase = true) }
				?: lines[1]
			val parts = memLine.trim().split(Regex("\\s+"))
			if (parts.size < 4) {
				return Result.failure(Exception("Invalid memory line format"))
			}

			val totalBytes = parseMemFieldToBytes(parts[1])
			val usedBytes = parseMemFieldToBytes(parts[2])
			val availableBytes = if (parts.size > 6) {
				parseMemFieldToBytes(parts[6])
			} else {
				parseMemFieldToBytes(parts[3])
			}

			val usagePercent = if (totalBytes > 0) {
				(usedBytes.toDouble() / totalBytes.toDouble()) * 100.0
			} else {
				0.0
			}

			Result.success(
				MemoryInfo(
					total = formatBytesAsGbMb(totalBytes),
					used = formatBytesAsGbMb(usedBytes),
					available = formatBytesAsGbMb(availableBytes),
					usagePercent = usagePercent
				)
			)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse memory info: ${e.message}", e))
		}
	}

	private fun parseMemFieldToBytes(raw: String): Long {
		val trimmed = raw.trim()
		if (trimmed.all { it.isDigit() }) {
			return trimmed.toLongOrNull() ?: 0L
		}
		return parseSizeToBytes(trimmed)
	}

	private fun formatBytesAsGbMb(bytes: Long): String {
		val value = bytes.coerceAtLeast(0)
		val kb = 1024.0
		val mb = kb * 1024
		val gb = mb * 1024
		return when {
			value >= gb -> String.format(Locale.US, "%.1f GB", value / gb)
			value >= mb -> String.format(Locale.US, "%.0f MB", value / mb)
			else -> String.format(Locale.US, "%.0f KB", value / kb)
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
			parseDiskOutput(output)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to parse disk info: ${e.message}", e))
		}
	}

	private fun parseDiskOutput(output: String): Result<DiskInfo> {
		return try {
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
			val kernelDeferred = async { sshCommandService.executeCommand(session, "uname -r", timeout = 5000) }
			val osReleaseDeferred = async { sshCommandService.executeCommand(session, "cat /etc/os-release", timeout = 5000) }
			val kernelVersion = kernelDeferred.await().getOrNull()?.trim() ?: "Unknown"
			val osRelease = osReleaseDeferred.await().getOrNull() ?: ""
			Result.success(parseSystemInfo(kernelVersion, osRelease))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get system info: ${e.message}", e))
		}
	}

	private fun parseSystemInfo(kernelVersionRaw: String, osRelease: String): SystemInfo {
			val kernelVersion = kernelVersionRaw.trim().ifBlank { "Unknown" }
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
			}

			return SystemInfo(
				osName = osName,
				osVersion = osVersion,
				kernelVersion = kernelVersion
			)
	}

	/**
	 * 获取网络信息
	 */
	override suspend fun getNetworkInfo(session: Session): Result<NetworkInfo> = coroutineScope {
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

internal fun snapshotSectionOf(output: String, name: String): String {
	val marker = "---VC_$name---"
	val start = output.indexOf(marker)
	if (start < 0) return ""
	val rest = output.substring(start + marker.length)
	val next = Regex("\n---VC_[A-Z_]+---").find(rest)
	val body = if (next == null) rest else rest.substring(0, next.range.first)
	return body.trim()
}

internal fun coreCpuFromSnapshot(output: String): Pair<Int, Double>? {
	if (!output.contains("---VC_END---") || !output.contains("---VC_CPU_CORES---")) return null
	val cores = snapshotSectionOf(output, "CPU_CORES")
		.lineSequence()
		.mapNotNull { it.trim().toIntOrNull() }
		.firstOrNull() ?: return null
	return cores to cpuUsagePercent(snapshotSectionOf(output, "CPU_STAT"))
}

internal fun cpuUsagePercent(statText: String): Double {
	val samples = statText.lineSequence()
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.mapNotNull { cpuCountersOf(it) }
		.take(2)
		.toList()
	if (samples.size < 2) return 0.0
	val totalDelta = samples[1].total - samples[0].total
	if (totalDelta <= 0L) return 0.0
	val idleDelta = samples[1].idle - samples[0].idle
	val busy = totalDelta - idleDelta
	return (busy.toDouble() / totalDelta.toDouble() * 100.0).coerceIn(0.0, 100.0)
}

private data class CpuCounters(val total: Long, val idle: Long)

private fun cpuCountersOf(line: String): CpuCounters? {
	val parts = line.split(Regex("\\s+"))
	if (parts.isEmpty() || parts[0] != "cpu") return null
	if (parts.size < 9) return null
	val values = LongArray(8)
	for (index in 0 until 8) {
		values[index] = parts[index + 1].toLongOrNull() ?: return null
	}
	return CpuCounters(total = values.sum(), idle = values[3] + values[4])
}

