package com.vcserver.services

import com.jcraft.jsch.Session
import com.vcserver.models.AuthType
import com.vcserver.models.DiskInfo
import com.vcserver.models.MemoryInfo
import com.vcserver.models.Server
import com.vcserver.models.ServerStatus
import com.vcserver.models.CpuInfo
import com.vcserver.utils.SecureStorage
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
	 * 获取服务器状态
	 */
	suspend fun getServerStatus(session: Session): Result<ServerStatus>

	/**
	 * 断开连接
	 */
	fun disconnect(session: Session)
}

/**
 * 服务器监控服务实现
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

			Result.success(ServerStatus(cpu = cpu, memory = memory, disk = disk, uptime = uptime))
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
			// 获取 CPU 核心数
			val coresResult = sshCommandService.executeCommand(session, "nproc")
			if (coresResult.isFailure) {
				return Result.failure(coresResult.exceptionOrNull() ?: Exception("Failed to get CPU cores"))
			}
			val cores = coresResult.getOrNull()?.toIntOrNull() ?: 1

			// 获取 CPU 使用率
			val cpuUsageResult = sshCommandService.executeCommand(
				session,
				"top -bn1 | grep 'Cpu(s)' | sed 's/.*, *\\([0-9.]*\\)%* id.*/\\1/' | awk '{print 100 - \$1}'"
			)
			val usagePercent = if (cpuUsageResult.isSuccess) {
				cpuUsageResult.getOrNull()?.toDoubleOrNull() ?: 0.0
			} else {
				// 备用方法：使用 /proc/stat
				val statResult = sshCommandService.executeCommand(session, "cat /proc/stat | head -1")
				if (statResult.isSuccess) {
					parseCpuUsageFromStat(statResult.getOrNull() ?: "")
				} else {
					0.0
				}
			}

			Result.success(CpuInfo(cores = cores, usagePercent = usagePercent))
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
	 * 获取系统启动时长
	 */
	private suspend fun getUptime(session: Session): Result<String> {
		return try {
			val result = sshCommandService.executeCommand(session, "uptime -p")
			if (result.isSuccess) {
				Result.success(result.getOrNull() ?: "")
			} else {
				// 备用方法
				val result2 = sshCommandService.executeCommand(session, "uptime")
				Result.success(result2.getOrNull() ?: "")
			}
		} catch (e: Exception) {
			Result.failure(Exception("Failed to get uptime: ${e.message}", e))
		}
	}

	/**
	 * 将大小字符串转换为字节数（用于计算百分比）
	 */
	private fun parseSizeToBytes(size: String): Long {
		val sizeUpper = size.uppercase().trim()
		val number = sizeUpper.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
		return when {
			sizeUpper.endsWith("TB") || sizeUpper.endsWith("T") -> (number * 1024 * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("GB") || sizeUpper.endsWith("G") -> (number * 1024 * 1024 * 1024).toLong()
			sizeUpper.endsWith("MB") || sizeUpper.endsWith("M") -> (number * 1024 * 1024).toLong()
			sizeUpper.endsWith("KB") || sizeUpper.endsWith("K") -> (number * 1024).toLong()
			sizeUpper.endsWith("B") -> number.toLong()
			else -> {
				// 如果没有单位，假设是字节
				if (number > 0) number.toLong() else 0L
			}
		}
	}
}

