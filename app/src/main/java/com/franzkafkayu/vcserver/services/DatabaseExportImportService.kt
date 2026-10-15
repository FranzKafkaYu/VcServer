package com.franzkafkayu.vcserver.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.franzkafkayu.vcserver.models.AppSettings
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.models.ServerGroup
import com.franzkafkayu.vcserver.repositories.ServerRepository
import com.franzkafkayu.vcserver.repositories.ServerGroupRepository
import com.franzkafkayu.vcserver.utils.JumpImportRow
import com.franzkafkayu.vcserver.utils.resolveImportedJumpHostId
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 导出数据格式
 */
data class ExportData(
	@SerializedName("version")
	val version: String = "1.0",
	@SerializedName("export_time")
	val exportTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
	@SerializedName("groups")
	val groups: List<ServerGroup>? = null,  // 分组列表（可选，向后兼容）
	@SerializedName("servers")
	val servers: List<Server>,
	@SerializedName("settings")
	val settings: AppSettings? = null  // 可选，保持向后兼容
)

/**
 * 数据库导出/导入服务接口
 */
interface DatabaseExportImportService {
	/**
	 * 导出服务器数据到文件
	 * @param context 上下文
	 * @param outputUri 输出文件 URI（使用 Storage Access Framework）
	 * @return 导出成功返回 Result.success，失败返回 Result.failure
	 */
	suspend fun exportDatabase(context: Context, outputUri: Uri): Result<Unit>

	/**
	 * 从文件导入服务器数据
	 * @param context 上下文
	 * @param inputUri 输入文件 URI（使用 Storage Access Framework）
	 * @param importStrategy 导入策略：true 表示覆盖重复项，false 表示跳过重复项
	 * @return 导入成功返回 Result.success(导入的服务器数量)，失败返回 Result.failure
	 */
	suspend fun importDatabase(
		context: Context,
		inputUri: Uri,
		importStrategy: Boolean = false
	): Result<Int>
}

/**
 * 数据库导出/导入服务实现
 */
class DatabaseExportImportServiceImpl(
	private val serverRepository: ServerRepository,
	private val serverGroupRepository: ServerGroupRepository? = null,
	private val settingsService: SettingsService? = null  // 可选，保持向后兼容
) : DatabaseExportImportService {

	private val gson: Gson = GsonBuilder()
		.setPrettyPrinting()
		.create()

	private val tag = "DatabaseExportImport"

	override suspend fun exportDatabase(context: Context, outputUri: Uri): Result<Unit> {
		return try {
			withContext(Dispatchers.IO) {
				// 获取所有服务器
				val servers = serverRepository.getAllServers().first()

				if (servers.isEmpty() && settingsService == null && serverGroupRepository == null) {
					return@withContext Result.failure(Exception("NO_DATA"))
				}

				// 获取分组（如果可用）
				val groups = serverGroupRepository?.getAllGroups()?.first()

				// 获取设置（如果可用）
				val settings = settingsService?.getSettings()?.first()

				// 创建导出数据
				val exportData = ExportData(
					groups = groups,
					servers = servers,
					settings = settings
				)

				// 序列化为 JSON
				val json = gson.toJson(exportData)

				// 写入文件
				context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
					outputStream.write(json.toByteArray(Charsets.UTF_8))
				} ?: throw Exception("UNABLE_TO_OPEN_OUTPUT_STREAM")

				val serverCount = servers.size
				val groupCount = groups?.size ?: 0
				val hasSettings = settings != null
				Log.d(tag, "导出成功: $serverCount 个服务器, $groupCount 个分组${if (hasSettings) ", 包含设置" else ""}")
				Result.success(Unit)
			}
		} catch (e: Exception) {
			Log.e(tag, "导出失败", e)
			Result.failure(e)
		}
	}

	override suspend fun importDatabase(
		context: Context,
		inputUri: Uri,
		importStrategy: Boolean
	): Result<Int> {
		return try {
			withContext(Dispatchers.IO) {
				// 读取文件
				val json = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
					inputStream.bufferedReader(Charsets.UTF_8).readText()
				} ?: throw Exception("UNABLE_TO_OPEN_INPUT_STREAM")

				// 解析 JSON
				val exportData = gson.fromJson(json, ExportData::class.java)

				// 验证格式
				if (exportData.version.isBlank()) {
					throw Exception("INVALID_FORMAT")
				}

				var importedCount = 0
				var importedGroupCount = 0

				// 导入分组（如果有）
				if (!exportData.groups.isNullOrEmpty()) {
					val existingGroups = serverGroupRepository?.getAllGroups()?.first() ?: emptyList()
					
					for (group in exportData.groups) {
						// 检查是否已存在同名分组
						val existingGroup = existingGroups.find { it.name == group.name }
						
						if (existingGroup != null) {
							// 分组已存在，根据导入策略决定是否更新
							// 对于分组，我们默认跳过（因为名称唯一，更新可能导致混淆）
							Log.d(tag, "跳过已存在分组: ${group.name}")
						} else {
							// 创建新分组（不保留原始ID）
							serverGroupRepository?.createGroup(group.name)?.fold(
								onSuccess = {
									importedGroupCount++
									Log.d(tag, "导入分组: ${group.name}")
								},
								onFailure = { e ->
									Log.w(tag, "导入分组失败: ${group.name}", e)
								}
							)
						}
					}
				}

				// 导入服务器（如果有）
				if (exportData.servers.isNotEmpty()) {
					// 获取现有服务器（用于检测重复）
					val existingServers = serverRepository.getAllServers().first()
					
					// 获取所有分组（包括新导入的）用于映射 groupId
					val allGroups = serverGroupRepository?.getAllGroups()?.first() ?: emptyList()

					val jumpRows = mutableListOf<JumpImportRow>()
					for (server in exportData.servers) {
						val importedEarlier = jumpRows.find { row ->
							row.host == server.host && row.port == server.port
						}
						val existingServer = existingServers.find { existing ->
							existing.host == server.host && existing.port == server.port
						}
						val duplicateLocalId = importedEarlier?.localId ?: existingServer?.id

						val mappedGroupId = if (server.groupId != null && !exportData.groups.isNullOrEmpty()) {
							val originalGroup = exportData.groups.find { it.id == server.groupId }
							originalGroup?.let { allGroups.find { it.name == originalGroup.name }?.id }
						} else {
							server.groupId
						}

						val serverToImport = server.copy(
							id = 0,
							groupId = mappedGroupId,
							jumpHostId = null,
							isPinned = server.isPinned
						)

						if (duplicateLocalId != null) {
							if (importStrategy) {
								serverRepository.updateServer(serverToImport.copy(id = duplicateLocalId))
								jumpRows += JumpImportRow(
									exportId = server.id,
									host = server.host,
									port = server.port,
									localId = duplicateLocalId,
									jumpExportId = server.jumpHostId,
									written = true
								)
								importedCount++
								Log.d(tag, "覆盖服务器: ${server.name}")
							} else {
								jumpRows += JumpImportRow(
									exportId = server.id,
									host = server.host,
									port = server.port,
									localId = duplicateLocalId,
									jumpExportId = server.jumpHostId,
									written = false
								)
								Log.d(tag, "跳过重复服务器: ${server.name}")
							}
						} else {
							val localId = serverRepository.insertServer(serverToImport)
							jumpRows += JumpImportRow(
								exportId = server.id,
								host = server.host,
								port = server.port,
								localId = localId,
								jumpExportId = server.jumpHostId,
								written = true
							)
							importedCount++
							Log.d(tag, "导入服务器: ${server.name}")
						}
					}
					for (row in jumpRows) {
						if (!row.written) continue
						val resolved = resolveImportedJumpHostId(jumpRows, row.jumpExportId)
						val current = serverRepository.getServerById(row.localId) ?: continue
						serverRepository.updateServer(current.copy(jumpHostId = resolved))
					}
				}

				// 导入设置（如果有且设置了服务）
				if (exportData.settings != null && settingsService != null) {
					val settings = exportData.settings
					try {
						settingsService.updateTheme(settings.theme)
						settingsService.updateLanguage(settings.language)
						settingsService.updateConnectionTimeout(settings.connectionTimeout)
						settingsService.updateDefaultSshPort(settings.defaultSshPort)
						settingsService.updateRefreshInterval(settings.refreshInterval)
						settingsService.updateDefaultProxy(
							type = settings.defaultProxyType,
							host = settings.defaultProxyHost,
							port = settings.defaultProxyPort,
							username = settings.defaultProxyUsername,
							password = settings.defaultProxyPassword
						)
						Log.d(tag, "导入设置成功")
					} catch (e: Exception) {
						Log.w(tag, "导入设置时出错，部分设置可能未导入", e)
						// 继续执行，不中断导入流程
					}
				}

				val logMessage = buildString {
					append("导入完成: $importedCount 个服务器")
					if (importedGroupCount > 0) {
						append(", $importedGroupCount 个分组")
					}
					if (exportData.settings != null && settingsService != null) {
						append(", 包含设置")
					}
				}
				Log.d(tag, logMessage)
				Result.success(importedCount)
			}
		} catch (e: Exception) {
			Log.e(tag, "导入失败", e)
			Result.failure(e)
		}
	}
}


