package com.franzkafkayu.vcserver.services

import com.jcraft.jsch.Session
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.DiskInfo
import com.franzkafkayu.vcserver.models.MemoryInfo
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerStatus
import com.franzkafkayu.vcserver.models.CpuInfo
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
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
	 * 获取服务器状�?
	 */
	suspend fun getServerStatus(session: Session): Result<ServerStatus>

	/**
	 * 断开连接
	 */
	fun disconnect(session: Session)
}

/**
 * 服务器监控服务实例
 */
class ServerMonitoringServiceImpl(
	private val sshAuthService: SshAuthenticationService,
	private val sshCommandService: SshCommandService,
	private val secureStorage: SecureStorage
) : ServerMonitoringService {

	override suspend fun connectToServer(server: Server): Result<Session> = withContext(Dispatchers.IO) {
		try {
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

			result
		} catch (e: Exception) {
			Result.failure(Exception("Failed to connect to server: ${e.message}", e))
		}
	}

	override suspend fun getServerStatus(session: Session): Result<ServerStatus> = withContext(Dispatchers.IO) {
		try {
			// 获取 CPU 信息
			val cpuResult = getCpuInfo(session)
			if (cpuResult.isFailure) {
				return@withContext Result.failure(cpuResult.exceptionOrNull() ?: Exception("Failed to get CPU info"))
			}
			val cpu = cpuResult.getOrNull() ?: return@withContext Result.failure(Exception("CPU info is null"))

			// 获取内存信息
			val memoryResult = getMemoryInfo(session)
			if (memoryResult.isFailure) {
				return@withContext Result.failure(memoryResult.exceptionOrNull() ?: Exception("Failed to get memory info"))
			}
			val memory = memoryResult.getOrNull() ?: return@withContext Result.failure(Exception("Memory info is null"))

			// 获取磁盘信息（汇总所有分区）
			val diskResult = getDiskInfo(session)
			if (diskResult.isFailure) {
				return@withContext Result.failure(diskResult.exceptionOrNull() ?: Exception("Failed to get disk info"))
			}
			val disk = diskResult.getOrNull()

			// 获取系统启动时长（可选）
			val uptime = getUptime(session).getOrNull()

			// 获取系统信息和内核版本（可选）
			val systemInfo = getSystemInfo(session).getOrNull()

			Result.success(ServerStatus(cpu = cpu, memory = memory, disk = disk, uptime = uptime, systemInfo = systemInfo))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get server status: ${e.message}", e))
		}
	}

	override fun disconnect(session: Session) {
		sshAuthService.closeSession(session)
	}

	/**
	 * 获取 CPU 信息
	 */
	private suspend fun getCpuInfo(session: Session): Result<CpuInfo> {
		return try {
			// 获取 CPU 核心�?
			val coresResult = sshCommandService.executeCommand(session, "nproc")
			if (coresResult.isFailure) {
				return Result.failure(coresResult.exceptionOrNull() ?: Exception("Failed to get CPU cores"))
			}
			val cores = coresResult.getOrNull()?.toIntOrNull() ?: 1

			// 获取 CPU 架构
			val archResult = sshCommandService.executeCommand(session, "uname -m")
			val architecture = if (archResult.isSuccess) {
				archResult.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
			} else {
				null
			}

			// 获取 CPU 型号
			// 优先使用 lscpu，如果不可用则使�?/proc/cpuinfo
			val modelResult = sshCommandService.executeCommand(
				session,
				"lscpu 2>/dev/null | grep -i 'Model name' | cut -d: -f2 | xargs || cat /proc/cpuinfo 2>/dev/null | grep -i 'model name' | head -1 | cut -d: -f2 | xargs"
			)
			val model = if (modelResult.isSuccess) {
				modelResult.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
			} else {
				null
			}

			// 获取 CPU 使用率
			val cpuUsageResult = sshCommandService.executeCommand(
				session,
				"top -bn1 | grep 'Cpu(s)' | sed 's/.*, *\\([0-9.]*\\)%* id.*/\\1/' | awk '{print 100 - \$1}'"
			)
			val usagePercent = if (cpuUsageResult.isSuccess) {
				cpuUsageResult.getOrNull()?.toDoubleOrNull() ?: 0.0
			} else {
				// 备用方法：使�?/proc/stat
				val statResult = sshCommandService.executeCommand(session, "cat /proc/stat | head -1")
				if (statResult.isSuccess) {
					parseCpuUsageFromStat(statResult.getOrNull() ?: "")
				} else {
					0.0
				}
			}

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
	 * �?/proc/stat 解析 CPU 使用率
	 */
	private fun parseCpuUsageFromStat(statLine: String): Double {
		// 简化实现，返回 0.0
		// 实际实现需要计算两次采样之间的差�?
		return 0.0
	}

	/**
	 * 获取内存信息
	 */
	private suspend fun getMemoryInfo(session: Session): Result<MemoryInfo> {
		return try {
			val result = sshCommandService.executeCommand(session, "free -h")
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
			val available = parts[6] // free 命令�?available �?

			// 计算使用�?
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
	 * 获取磁盘信息（汇总所有分区）
	 */
	private suspend fun getDiskInfo(session: Session): Result<DiskInfo> {
		return try {
			val result = sshCommandService.executeCommand(session, "df -h")
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

			// 跳过第一行（标题行），汇总所有真实磁盘分�?
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

				// 排除 loop 设备、tmpfs、udev 等虚拟文件系�?
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
	 * 获取系统启动时长
	 */
	private suspend fun getUptime(session: Session): Result<String> {
		return try {
			val result = sshCommandService.executeCommand(session, "uptime -p")
			val uptimeText = if (result.isSuccess) {
				result.getOrNull() ?: ""
			} else {
				// 备用方法
				val result2 = sshCommandService.executeCommand(session, "uptime")
				result2.getOrNull() ?: ""
			}
			
			// 格式�?uptime 输出
			val formatted = formatUptime(uptimeText)
			Result.success(formatted)
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get uptime: ${e.message}", e))
		}
	}

	/**
	 * 格式 uptime 输出"xxx days,xx hours,xxx minutes" 格式
	 * 输入示例: "up 2 days, 3 hours, 45 minutes" �?"up 1 hour, 30 minutes"
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
		
		// 匹配 years（不区分大小写）�? year = 365 days
		val yearsMatch = Regex("(\\d+)\\s+year", RegexOption.IGNORE_CASE).find(text)
		yearsMatch?.let { 
			val years = it.groupValues[1].toIntOrNull() ?: 0
			totalDays += years * 365
		}
		
		// 匹配 weeks（不区分大小写）�? week = 7 days
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
			// 尝试解析秒数（如果输出是秒数格式�?
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
		
		// 如果所有值都为空，返回默认值
		if (parts.isEmpty()) {
			return "0 minutes"
		}
		
		return parts.joinToString(", ")
	}

	/**
	 * 获取系统信息和内核版�?
	 */
	private suspend fun getSystemInfo(session: Session): Result<com.franzkafkayu.vcserver.models.SystemInfo> {
		return try {
			// 获取内核版本
			val kernelResult = sshCommandService.executeCommand(session, "uname -r")
			val kernelVersion = kernelResult.getOrNull()?.trim() ?: "Unknown"

			// 获取操作系统信息
			val osResult = sshCommandService.executeCommand(session, "cat /etc/os-release")
			val osRelease = osResult.getOrNull() ?: ""

			var osName = "Unknown"
			var osVersion = "Unknown"

			if (osRelease.isNotEmpty()) {
				// 解析 /etc/os-release 文件
				val lines = osRelease.lines()
				for (line in lines) {
					when {
						line.startsWith("PRETTY_NAME=") -> {
							// 提取操作系统名称和版�?
							val value = line.substringAfter("=").trim('"', '\'')
							// 尝试分离名称和版本（例如 "Ubuntu 22.04.3 LTS"�?
							val parts = value.split(" ", limit = 2)
							if (parts.size >= 1) {
								osName = parts[0]
								if (parts.size >= 2) {
									// 提取版本号（例如�?"22.04.3 LTS" 中提�?"22.04"�?
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
				// 备用方法：使�?uname -a
				val unameResult = sshCommandService.executeCommand(session, "uname -a")
				val unameOutput = unameResult.getOrNull() ?: ""
				if (unameOutput.isNotEmpty()) {
					// �?uname -a 输出中提取信�?
					// 格式通常�? Linux hostname kernel-version #version date time arch
					val parts = unameOutput.split(" ")
					if (parts.size >= 3) {
						osName = parts[0] // "Linux"
						// 尝试从其他部分提取更多信息
					}
				}
			}

			Result.success(com.franzkafkayu.vcserver.models.SystemInfo(
				osName = osName,
				osVersion = osVersion,
				kernelVersion = kernelVersion
			))
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get system info: ${e.message}", e))
		}
	}

	/**
	 * 将大小字符串转换为字节数（用于计算百分比�?
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
			// 短单位（T, G, M, K�? 最后检查，避免误匹�?
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

