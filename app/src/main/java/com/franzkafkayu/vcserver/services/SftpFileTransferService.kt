package com.franzkafkayu.vcserver.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * SFTP 文件传输服务接口
 */
interface SftpFileTransferService {
	/**
	 * 文件传输进度回调
	 */
	interface FileTransferProgressCallback {
		/**
		 * 传输进度更新
		 * @param transferred 已传输字节数
		 * @param total 总字节数
		 */
		fun onProgress(transferred: Long, total: Long)
	}

	/**
	 * 上传文件到远程服务器
	 * @param session SSH Session
	 * @param localUri 本地文件 URI
	 * @param remotePath 远程服务器目标路径
	 * @param progressCallback 进度回调（可选）
	 * @return 上传成功返回 Result.success，失败返回 Result.failure
	 */
	suspend fun uploadFile(
		session: Session,
		localUri: Uri,
		remotePath: String,
		progressCallback: FileTransferProgressCallback? = null
	): Result<Unit>

	/**
	 * 从远程服务器下载文件
	 * @param session SSH Session
	 * @param remotePath 远程服务器文件路径
	 * @param localUri 本地保存位置 URI
	 * @param progressCallback 进度回调（可选）
	 * @return 下载成功返回 Result.success，失败返回 Result.failure
	 */
	suspend fun downloadFile(
		session: Session,
		remotePath: String,
		localUri: Uri,
		progressCallback: FileTransferProgressCallback? = null
	): Result<Unit>
}

/**
 * SFTP 文件传输服务实现
 */
class SftpFileTransferServiceImpl(
	private val context: Context
) : SftpFileTransferService {

	private val tag = "SftpFileTransfer"

	override suspend fun uploadFile(
		session: Session,
		localUri: Uri,
		remotePath: String,
		progressCallback: SftpFileTransferService.FileTransferProgressCallback?
	): Result<Unit> {
		Log.d(tag, "开始上传文件: localUri=$localUri, remotePath=$remotePath")
		return try {
			withContext(Dispatchers.IO) {
				// 检查 session 是否连接
				Log.d(tag, "检查 SSH Session 连接状态: isConnected=${session.isConnected}")
				if (!session.isConnected) {
					Log.e(tag, "SSH Session 未连接")
					return@withContext Result.failure(Exception("SESSION_NOT_CONNECTED: SSH session is not connected"))
				}

				// 创建 SFTP Channel
				Log.d(tag, "创建 SFTP Channel...")
				val channel = try {
					session.openChannel("sftp") as? ChannelSftp
				} catch (e: Exception) {
					Log.e(tag, "创建 SFTP Channel 失败", e)
					return@withContext Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: ${e.message}"))
				}
				
				if (channel == null) {
					Log.e(tag, "SFTP Channel 为 null")
					return@withContext Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: Channel is null"))
				}

				try {
					Log.d(tag, "连接 SFTP Channel...")
					channel.connect()
					Log.d(tag, "SFTP Channel 连接成功")

					// 获取文件大小（用于进度显示）
					val fileSize = try {
						context.contentResolver.openFileDescriptor(localUri, "r")?.use { pfd ->
							val size = pfd.statSize
							Log.d(tag, "本地文件大小: $size bytes")
							size
						} ?: run {
							Log.w(tag, "无法获取文件大小，使用 0")
							0L
						}
					} catch (e: Exception) {
						Log.w(tag, "获取文件大小失败: ${e.message}", e)
						0L
					}

					// 打开本地文件输入流
					Log.d(tag, "打开本地文件输入流...")
					val inputStream = try {
						context.contentResolver.openInputStream(localUri)
					} catch (e: Exception) {
						Log.e(tag, "打开本地文件输入流失败: ${e.message}", e)
						throw Exception("UNABLE_TO_OPEN_LOCAL_FILE: ${e.message}")
					}
					
					if (inputStream == null) {
						Log.e(tag, "本地文件输入流为 null")
						throw Exception("UNABLE_TO_OPEN_LOCAL_FILE: InputStream is null")
					}
					Log.d(tag, "本地文件输入流打开成功")

					// 检查远程目录是否存在
					try {
						val remoteDir = remotePath.substringBeforeLast("/")
						if (remoteDir.isNotEmpty()) {
							Log.d(tag, "检查远程目录是否存在: $remoteDir")
							channel.stat(remoteDir)
							Log.d(tag, "远程目录存在")
						}
					} catch (e: SftpException) {
						if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
							Log.e(tag, "远程目录不存在: ${e.message}")
							throw Exception("PARENT_DIR_NOT_EXISTS: ${e.message}")
						}
					}

					// 使用带进度的上传方法
					Log.d(tag, "开始上传文件到远程路径: $remotePath")
					channel.put(
						inputStream,
						remotePath,
						object : SftpProgressMonitor {
							private var transferred: Long = 0

							override fun count(count: Long): Boolean {
								transferred += count
								progressCallback?.onProgress(transferred, fileSize)
								if (transferred % (1024 * 1024) == 0L || transferred == fileSize) {
									Log.d(tag, "上传进度: $transferred / $fileSize bytes")
								}
								return true
							}

							override fun end() {
								Log.d(tag, "上传完成")
							}

							override fun init(op: Int, src: String, dest: String, max: Long) {
								Log.d(tag, "初始化上传: op=$op, src=$src, dest=$dest, max=$max")
								transferred = 0
							}
						}
					)

					inputStream.close()
					Log.d(tag, "文件上传成功: $remotePath")
					Result.success(Unit)
				} catch (e: SftpException) {
					Log.e(tag, "SFTP 上传错误: id=${e.id}, message=${e.message}", e)
					when (e.id) {
						ChannelSftp.SSH_FX_PERMISSION_DENIED -> {
							Result.failure(Exception("PERMISSION_DENIED: ${e.message}"))
						}
						ChannelSftp.SSH_FX_NO_SUCH_FILE -> {
							Result.failure(Exception("PARENT_DIR_NOT_EXISTS: ${e.message}"))
						}
						ChannelSftp.SSH_FX_FAILURE -> {
							// 检查是否是目录错误
							if (e.message?.contains("directory", ignoreCase = true) == true) {
								Result.failure(Exception("REMOTE_PATH_IS_DIRECTORY: ${e.message}"))
							} else {
								Result.failure(Exception("SFTP_FAILURE: ${e.message}"))
							}
						}
						4 -> {
							// SFTP 错误代码 4 通常表示路径是目录
							Result.failure(Exception("REMOTE_PATH_IS_DIRECTORY: ${e.message}"))
						}
						else -> {
							// 检查错误消息中是否包含 "directory"
							if (e.message?.contains("directory", ignoreCase = true) == true) {
								Result.failure(Exception("REMOTE_PATH_IS_DIRECTORY: ${e.message}"))
							} else {
								Result.failure(Exception("SFTP_ERROR_${e.id}: ${e.message}"))
							}
						}
					}
				} catch (e: Exception) {
					Log.e(tag, "上传文件错误: ${e.javaClass.simpleName}, message=${e.message}", e)
					Result.failure(Exception("UPLOAD_ERROR: ${e.javaClass.simpleName} - ${e.message}"))
				} finally {
					try {
						channel.disconnect()
						Log.d(tag, "SFTP Channel 已断开")
					} catch (e: Exception) {
						Log.w(tag, "断开 SFTP Channel 时出错: ${e.message}")
					}
				}
			}
		} catch (e: JSchException) {
			Log.e(tag, "创建 SFTP Channel 错误: ${e.message}", e)
			Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: ${e.message}"))
		} catch (e: Exception) {
			Log.e(tag, "上传文件错误: ${e.javaClass.simpleName}, message=${e.message}", e)
			Result.failure(Exception("UPLOAD_ERROR: ${e.javaClass.simpleName} - ${e.message}"))
		}
	}

	override suspend fun downloadFile(
		session: Session,
		remotePath: String,
		localUri: Uri,
		progressCallback: SftpFileTransferService.FileTransferProgressCallback?
	): Result<Unit> {
		Log.d(tag, "开始下载文件: remotePath=$remotePath, localUri=$localUri")
		return try {
			withContext(Dispatchers.IO) {
				// 检查 session 是否连接
				Log.d(tag, "检查 SSH Session 连接状态: isConnected=${session.isConnected}")
				if (!session.isConnected) {
					Log.e(tag, "SSH Session 未连接")
					return@withContext Result.failure(Exception("SESSION_NOT_CONNECTED: SSH session is not connected"))
				}

				// 创建 SFTP Channel
				Log.d(tag, "创建 SFTP Channel...")
				val channel = try {
					session.openChannel("sftp") as? ChannelSftp
				} catch (e: Exception) {
					Log.e(tag, "创建 SFTP Channel 失败", e)
					return@withContext Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: ${e.message}"))
				}
				
				if (channel == null) {
					Log.e(tag, "SFTP Channel 为 null")
					return@withContext Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: Channel is null"))
				}

				try {
					Log.d(tag, "连接 SFTP Channel...")
					channel.connect()
					Log.d(tag, "SFTP Channel 连接成功")

					// 检查远程文件是否存在并获取文件大小
					Log.d(tag, "检查远程文件是否存在: $remotePath")
					val fileSize = try {
						val stat = channel.stat(remotePath)
						val size = stat.size.toLong()
						Log.d(tag, "远程文件存在，大小: $size bytes")
						size
					} catch (e: SftpException) {
						Log.e(tag, "检查远程文件失败: id=${e.id}, message=${e.message}", e)
						when (e.id) {
							ChannelSftp.SSH_FX_NO_SUCH_FILE -> {
								return@withContext Result.failure(Exception("FILE_NOT_EXISTS: ${e.message}"))
							}
							ChannelSftp.SSH_FX_PERMISSION_DENIED -> {
								return@withContext Result.failure(Exception("PERMISSION_DENIED: ${e.message}"))
							}
							else -> {
								return@withContext Result.failure(Exception("SFTP_ERROR_${e.id}: ${e.message}"))
							}
						}
					}

					// 打开本地文件输出流
					Log.d(tag, "打开本地文件输出流...")
					val outputStream = try {
						context.contentResolver.openOutputStream(localUri)
					} catch (e: Exception) {
						Log.e(tag, "打开本地文件输出流失败: ${e.message}", e)
						throw Exception("UNABLE_TO_OPEN_LOCAL_FILE: ${e.message}")
					}
					
					if (outputStream == null) {
						Log.e(tag, "本地文件输出流为 null")
						throw Exception("UNABLE_TO_OPEN_LOCAL_FILE: OutputStream is null")
					}
					Log.d(tag, "本地文件输出流打开成功")

					// 使用带进度的下载方法
					Log.d(tag, "开始下载文件...")
					channel.get(
						remotePath,
						outputStream,
						object : SftpProgressMonitor {
							private var transferred: Long = 0

							override fun count(count: Long): Boolean {
								transferred += count
								progressCallback?.onProgress(transferred, fileSize)
								if (transferred % (1024 * 1024) == 0L || transferred == fileSize) {
									Log.d(tag, "下载进度: $transferred / $fileSize bytes")
								}
								return true
							}

							override fun end() {
								Log.d(tag, "下载完成")
							}

							override fun init(op: Int, src: String, dest: String, max: Long) {
								Log.d(tag, "初始化下载: op=$op, src=$src, dest=$dest, max=$max")
								transferred = 0
							}
						}
					)

					outputStream.close()
					Log.d(tag, "文件下载成功: $remotePath")
					Result.success(Unit)
				} catch (e: SftpException) {
					Log.e(tag, "SFTP 下载错误: id=${e.id}, message=${e.message}", e)
					when (e.id) {
						ChannelSftp.SSH_FX_PERMISSION_DENIED -> {
							Result.failure(Exception("PERMISSION_DENIED: ${e.message}"))
						}
						ChannelSftp.SSH_FX_NO_SUCH_FILE -> {
							Result.failure(Exception("FILE_NOT_EXISTS: ${e.message}"))
						}
						ChannelSftp.SSH_FX_FAILURE -> {
							Result.failure(Exception("SFTP_FAILURE: ${e.message}"))
						}
						else -> {
							Result.failure(Exception("SFTP_ERROR_${e.id}: ${e.message}"))
						}
					}
				} catch (e: Exception) {
					Log.e(tag, "下载文件错误: ${e.javaClass.simpleName}, message=${e.message}", e)
					Result.failure(Exception("DOWNLOAD_ERROR: ${e.javaClass.simpleName} - ${e.message}"))
				} finally {
					try {
						channel.disconnect()
						Log.d(tag, "SFTP Channel 已断开")
					} catch (e: Exception) {
						Log.w(tag, "断开 SFTP Channel 时出错: ${e.message}")
					}
				}
			}
		} catch (e: JSchException) {
			Log.e(tag, "创建 SFTP Channel 错误: ${e.message}", e)
			Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: ${e.message}"))
		} catch (e: Exception) {
			Log.e(tag, "下载文件错误: ${e.javaClass.simpleName}, message=${e.message}", e)
			Result.failure(Exception("DOWNLOAD_ERROR: ${e.javaClass.simpleName} - ${e.message}"))
		}
	}
}

