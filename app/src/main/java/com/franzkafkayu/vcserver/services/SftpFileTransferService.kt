package com.franzkafkayu.vcserver.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.franzkafkayu.vcserver.ssh.SshClientSession
import com.franzkafkayu.vcserver.ssh.SshSftp
import com.franzkafkayu.vcserver.ssh.SshSftpCode
import com.franzkafkayu.vcserver.ssh.SshSftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

data class SftpRemoteEntry(
	val name: String,
	val isDirectory: Boolean,
	val size: Long
)

data class SftpDirectoryListing(
	val path: String,
	val entries: List<SftpRemoteEntry>
)

private const val SFTP_TEXT_MAX_BYTES = 1024 * 1024L

/**
 * SFTP 文件传输服务接口
 */
interface SftpFileTransferService {
	interface FileTransferProgressCallback {
		fun onProgress(transferred: Long, total: Long)
	}

	suspend fun uploadFile(
		session: SshClientSession,
		localUri: Uri,
		remotePath: String,
		progressCallback: FileTransferProgressCallback? = null
	): Result<Unit>

	suspend fun downloadFile(
		session: SshClientSession,
		remotePath: String,
		localUri: Uri,
		progressCallback: FileTransferProgressCallback? = null
	): Result<Unit>

	suspend fun listDirectory(
		session: SshClientSession,
		path: String? = null
	): Result<SftpDirectoryListing>

	suspend fun readTextFile(
		session: SshClientSession,
		remotePath: String
	): Result<String>

	suspend fun writeTextFile(
		session: SshClientSession,
		remotePath: String,
		content: String
	): Result<Unit>

	suspend fun createDirectory(session: SshClientSession, remotePath: String): Result<Unit>

	suspend fun createFile(session: SshClientSession, remotePath: String): Result<Unit>

	suspend fun deletePath(session: SshClientSession, remotePath: String, isDirectory: Boolean): Result<Unit>

	suspend fun renamePath(session: SshClientSession, fromPath: String, newName: String): Result<Unit>
}

/**
 * SFTP 文件传输服务实现
 */
class SftpFileTransferServiceImpl(
	private val context: Context
) : SftpFileTransferService {

	private val tag = "SftpFileTransfer"

	override suspend fun uploadFile(
		session: SshClientSession,
		localUri: Uri,
		remotePath: String,
		progressCallback: SftpFileTransferService.FileTransferProgressCallback?
	): Result<Unit> {
		Log.d(tag, "开始上传文件: localUri=$localUri, remotePath=$remotePath")
		return withContext(Dispatchers.IO) {
			if (!session.isConnected) {
				return@withContext Result.failure(Exception("SESSION_NOT_CONNECTED: SSH session is not connected"))
			}
			val sftp = openSftp(session).getOrElse { return@withContext Result.failure(it) }
			try {
				val fileSize = try {
					context.contentResolver.openFileDescriptor(localUri, "r")?.use { it.statSize } ?: 0L
				} catch (e: Exception) {
					Log.w(tag, "获取文件大小失败: ${e.message}", e)
					0L
				}
				val raw = context.contentResolver.openInputStream(localUri)
					?: return@withContext Result.failure(Exception("UNABLE_TO_OPEN_LOCAL_FILE: InputStream is null"))
				raw.use { input ->
					val remoteDir = remotePath.substringBeforeLast("/")
					if (remoteDir.isNotEmpty() && !sftp.exists(remoteDir)) {
						throw Exception("PARENT_DIR_NOT_EXISTS: $remoteDir")
					}
					val counting = CountingInputStream(input, fileSize, progressCallback)
					sftp.put(counting, remotePath, fileSize)
				}
				Log.d(tag, "文件上传成功: $remotePath")
				Result.success(Unit)
			} catch (e: Exception) {
				Result.failure(mapUpload(e))
			} finally {
				sftp.close()
			}
		}
	}

	override suspend fun downloadFile(
		session: SshClientSession,
		remotePath: String,
		localUri: Uri,
		progressCallback: SftpFileTransferService.FileTransferProgressCallback?
	): Result<Unit> {
		Log.d(tag, "开始下载文件: remotePath=$remotePath, localUri=$localUri")
		return withContext(Dispatchers.IO) {
			if (!session.isConnected) {
				return@withContext Result.failure(Exception("SESSION_NOT_CONNECTED: SSH session is not connected"))
			}
			val sftp = openSftp(session).getOrElse { return@withContext Result.failure(it) }
			try {
				val fileSize = try {
					sftp.statSize(remotePath)
				} catch (e: SshSftpException) {
					return@withContext Result.failure(Exception(statusMessage(e, missing = "FILE_NOT_EXISTS")))
				}
				val raw = context.contentResolver.openOutputStream(localUri)
					?: return@withContext Result.failure(Exception("UNABLE_TO_OPEN_LOCAL_FILE: OutputStream is null"))
				raw.use { output ->
					val counting = CountingOutputStream(output, fileSize, progressCallback)
					sftp.get(remotePath, counting)
				}
				Log.d(tag, "文件下载成功: $remotePath")
				Result.success(Unit)
			} catch (e: Exception) {
				Result.failure(mapDownload(e))
			} finally {
				sftp.close()
			}
		}
	}

	override suspend fun listDirectory(
		session: SshClientSession,
		path: String?
	): Result<SftpDirectoryListing> {
		return withSftp(session) { sftp ->
			val requested = path?.trim().orEmpty()
			val listing = if (requested.isEmpty()) {
				try {
					listingOf(sftp, sftp.pwd())
				} catch (e: Exception) {
					Log.w(tag, "列出家目录失败，回退 /: ${e.message}")
					listingOf(sftp, "/")
				}
			} else {
				listingOf(sftp, requested)
			}
			Log.d(tag, "列出目录 ${listing.path} 共 ${listing.entries.size} 项")
			listing
		}
	}

	override suspend fun readTextFile(
		session: SshClientSession,
		remotePath: String
	): Result<String> {
		return withSftp(session) { sftp ->
			val size = sftp.statSize(remotePath)
			if (size > SFTP_TEXT_MAX_BYTES) {
				throw Exception("FILE_TOO_LARGE: $size")
			}
			val bytes = sftp.readBytes(remotePath)
			if (bytes.any { it == 0.toByte() }) {
				throw Exception("FILE_BINARY: contains NUL")
			}
			String(bytes, Charsets.UTF_8)
		}
	}

	override suspend fun writeTextFile(
		session: SshClientSession,
		remotePath: String,
		content: String
	): Result<Unit> {
		return withSftp(session) { sftp ->
			val bytes = content.toByteArray(Charsets.UTF_8)
			ByteArrayInputStream(bytes).use { input ->
				sftp.put(input, remotePath, bytes.size.toLong())
			}
			Unit
		}
	}

	override suspend fun createDirectory(session: SshClientSession, remotePath: String): Result<Unit> {
		return withSftp(session) { sftp ->
			ensureCreatable(sftp, remotePath)
			sftp.mkdir(remotePath)
		}
	}

	override suspend fun createFile(session: SshClientSession, remotePath: String): Result<Unit> {
		return withSftp(session) { sftp ->
			ensureCreatable(sftp, remotePath)
			ByteArrayInputStream(ByteArray(0)).use { input ->
				sftp.put(input, remotePath, 0L)
			}
			Unit
		}
	}

	override suspend fun renamePath(
		session: SshClientSession,
		fromPath: String,
		newName: String
	): Result<Unit> {
		return withSftp(session) { sftp ->
			val from = fromPath.trim()
			if (from.isEmpty() || from == "/") {
				throw Exception("NAME_INVALID: cannot rename root")
			}
			val leaf = newName.trim()
			validateLeafName(leaf)
			val slash = from.lastIndexOf('/')
			val dest = when {
				slash < 0 -> leaf
				slash == 0 -> "/$leaf"
				else -> from.substring(0, slash + 1) + leaf
			}
			if (from == dest) return@withSftp Unit
			ensureCreatable(sftp, dest)
			sftp.rename(from, dest)
		}
	}

	override suspend fun deletePath(
		session: SshClientSession,
		remotePath: String,
		isDirectory: Boolean
	): Result<Unit> {
		return withSftp(session) { sftp ->
			val path = remotePath.trim()
			if (path.isEmpty() || path == "/") {
				throw Exception("NAME_INVALID: cannot delete root")
			}
			deleteRecursive(sftp, path, isDirectory)
		}
	}

	private fun ensureCreatable(sftp: SshSftp, remotePath: String) {
		validateLeafName(remotePath.substringAfterLast('/'))
		if (sftp.exists(remotePath)) {
			throw Exception("NAME_EXISTS: $remotePath")
		}
	}

	private fun validateLeafName(name: String) {
		val n = name.trim()
		if (n.isEmpty() || n == "." || n == ".." || n.contains('/') || n.contains('\\')) {
			throw Exception("NAME_INVALID: $name")
		}
	}

	private fun deleteRecursive(sftp: SshSftp, path: String, isDirectory: Boolean) {
		if (isDirectory) {
			sftp.list(path).forEach { entry ->
				val child = if (path == "/") "/${entry.name}" else "$path/${entry.name}"
				deleteRecursive(sftp, child, entry.isDirectory)
			}
			sftp.rmdir(path)
		} else {
			sftp.rm(path)
		}
	}

	private fun openSftp(session: SshClientSession): Result<SshSftp> {
		return try {
			Result.success(session.openSftp())
		} catch (e: Exception) {
			Log.e(tag, "创建 SFTP Channel 失败", e)
			Result.failure(Exception("UNABLE_TO_CREATE_SFTP_CHANNEL: ${e.message}"))
		}
	}

	private suspend fun <T> withSftp(
		session: SshClientSession,
		block: (SshSftp) -> T
	): Result<T> = withContext(Dispatchers.IO) {
		if (!session.isConnected) {
			return@withContext Result.failure(Exception("SESSION_NOT_CONNECTED: SSH session is not connected"))
		}
		val sftp = openSftp(session).getOrElse { return@withContext Result.failure(it) }
		try {
			Result.success(block(sftp))
		} catch (e: SshSftpException) {
			Log.e(tag, "SFTP 错误: code=${e.code}, message=${e.message}", e)
			Result.failure(Exception(statusMessage(e, missing = "FILE_NOT_EXISTS")))
		} catch (e: Exception) {
			val message = e.message ?: e.javaClass.simpleName
			if (message.startsWith("FILE_TOO_LARGE") ||
				message.startsWith("FILE_BINARY") ||
				message.startsWith("SESSION_NOT_CONNECTED") ||
				message.startsWith("NAME_EXISTS") ||
				message.startsWith("NAME_INVALID")
			) {
				Result.failure(e)
			} else {
				Result.failure(Exception("SFTP_ERROR: $message"))
			}
		} finally {
			sftp.close()
		}
	}

	private fun listingOf(sftp: SshSftp, path: String): SftpDirectoryListing {
		val entries = sftp.list(path).map { entry ->
			SftpRemoteEntry(
				name = entry.name,
				isDirectory = entry.isDirectory,
				size = entry.size
			)
		}.sortedWith(compareByDescending<SftpRemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
		val resolved = if (path == "." || path.isEmpty()) sftp.pwd() else path
		return SftpDirectoryListing(path = resolved, entries = entries)
	}

	private fun mapUpload(e: Exception): Exception {
		if (e is SshSftpException) {
			val message = e.message.orEmpty()
			return Exception(
				when (e.code) {
					SshSftpCode.PERMISSION_DENIED -> "PERMISSION_DENIED: $message"
					SshSftpCode.NO_SUCH_FILE -> "PARENT_DIR_NOT_EXISTS: $message"
					SshSftpCode.FAILURE -> if (message.contains("directory", ignoreCase = true)) {
						"REMOTE_PATH_IS_DIRECTORY: $message"
					} else {
						"SFTP_FAILURE: $message"
					}
					SshSftpCode.OTHER -> if (message.contains("directory", ignoreCase = true)) {
						"REMOTE_PATH_IS_DIRECTORY: $message"
					} else {
						"SFTP_ERROR: $message"
					}
				}
			)
		}
		val message = e.message.orEmpty()
		if (message.startsWith("PARENT_DIR_NOT_EXISTS") || message.startsWith("UNABLE_TO_OPEN_LOCAL_FILE")) {
			return e
		}
		Log.e(tag, "上传文件错误: ${e.javaClass.simpleName}, message=$message", e)
		return Exception("UPLOAD_ERROR: ${e.javaClass.simpleName} - $message")
	}

	private fun mapDownload(e: Exception): Exception {
		if (e is SshSftpException) {
			return Exception(statusMessage(e, missing = "FILE_NOT_EXISTS"))
		}
		val message = e.message.orEmpty()
		if (message.startsWith("FILE_NOT_EXISTS") ||
			message.startsWith("PERMISSION_DENIED") ||
			message.startsWith("UNABLE_TO_OPEN_LOCAL_FILE")
		) {
			return e
		}
		Log.e(tag, "下载文件错误: ${e.javaClass.simpleName}, message=$message", e)
		return Exception("DOWNLOAD_ERROR: ${e.javaClass.simpleName} - $message")
	}

	private fun statusMessage(e: SshSftpException, missing: String): String {
		val message = e.message.orEmpty()
		return when (e.code) {
			SshSftpCode.PERMISSION_DENIED -> "PERMISSION_DENIED: $message"
			SshSftpCode.NO_SUCH_FILE -> "$missing: $message"
			SshSftpCode.FAILURE -> "SFTP_FAILURE: $message"
			SshSftpCode.OTHER -> "SFTP_ERROR: $message"
		}
	}
}

private class CountingInputStream(
	delegate: InputStream,
	private val total: Long,
	private val callback: SftpFileTransferService.FileTransferProgressCallback?
) : FilterInputStream(delegate) {
	private var transferred = 0L

	override fun read(): Int {
		val value = super.read()
		if (value >= 0) tick(1)
		return value
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val count = super.read(b, off, len)
		if (count > 0) tick(count.toLong())
		return count
	}

	private fun tick(count: Long) {
		transferred += count
		callback?.onProgress(transferred, total)
	}
}

private class CountingOutputStream(
	delegate: OutputStream,
	private val total: Long,
	private val callback: SftpFileTransferService.FileTransferProgressCallback?
) : FilterOutputStream(delegate) {
	private var transferred = 0L

	override fun write(b: Int) {
		super.write(b)
		tick(1)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		super.write(b, off, len)
		tick(len.toLong())
	}

	private fun tick(count: Long) {
		transferred += count
		callback?.onProgress(transferred, total)
	}
}
