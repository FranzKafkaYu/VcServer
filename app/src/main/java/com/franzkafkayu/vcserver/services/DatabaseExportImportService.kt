package com.franzkafkayu.vcserver.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.repositories.ServerRepository
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
	@SerializedName("servers")
	val servers: List<Server>
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
	private val serverRepository: ServerRepository
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

				if (servers.isEmpty()) {
					return@withContext Result.failure(Exception("NO_SERVERS"))
				}

				// 创建导出数据
				val exportData = ExportData(servers = servers)

				// 序列化为 JSON
				val json = gson.toJson(exportData)

				// 写入文件
				context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
					outputStream.write(json.toByteArray(Charsets.UTF_8))
				} ?: throw Exception("UNABLE_TO_OPEN_OUTPUT_STREAM")

				Log.d(tag, "导出成功: ${servers.size} 个服务器")
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
				if (exportData.version.isBlank() || exportData.servers.isEmpty()) {
					throw Exception("INVALID_FORMAT")
				}

				// 获取现有服务器（用于检测重复）
				val existingServers = serverRepository.getAllServers().first()

				var importedCount = 0

				// 导入每个服务器
				for (server in exportData.servers) {
					// 检查是否已存在（通过主机和端口判断）
					val isDuplicate = existingServers.any { existing ->
						existing.host == server.host && existing.port == server.port
					}

					if (isDuplicate) {
						if (importStrategy) {
							// 覆盖：更新现有服务器
							val existingServer = existingServers.first { existing ->
								existing.host == server.host && existing.port == server.port
							}
							val updatedServer = server.copy(id = existingServer.id)
							serverRepository.updateServer(updatedServer)
							importedCount++
							Log.d(tag, "覆盖服务器: ${server.name}")
						} else {
							// 跳过：不导入重复项
							Log.d(tag, "跳过重复服务器: ${server.name}")
						}
					} else {
						// 导入新服务器
						serverRepository.insertServer(server)
						importedCount++
						Log.d(tag, "导入服务器: ${server.name}")
					}
				}

				Log.d(tag, "导入完成: $importedCount 个服务器")
				Result.success(importedCount)
			}
		} catch (e: Exception) {
			Log.e(tag, "导入失败", e)
			Result.failure(e)
		}
	}
}

