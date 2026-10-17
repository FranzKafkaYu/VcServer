package com.franzkafkayu.vcserver.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import net.schmizz.sshj.xfer.InMemoryDestFile
import net.schmizz.sshj.xfer.InMemorySourceFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 已认证的 SSH 连接。UI 只持有本类型，不接触 sshj。
 */
class SshClientSession internal constructor(
	private val client: SSHClient
) {
	val isConnected: Boolean
		get() = try {
			client.isConnected && client.isAuthenticated
		} catch (_: Exception) {
			false
		}

	fun disconnect() {
		try {
			if (client.isConnected) {
				client.disconnect()
			}
		} catch (_: Exception) {
		}
	}

	internal fun openDirect(host: String, port: Int): DirectConnection {
		return client.newDirectConnection(host, port)
	}

	fun exec(command: String, timeoutMs: Long): String {
		val session = client.startSession()
		var commandChannel: Session.Command? = null
		try {
			commandChannel = session.exec(command)
			val commandRef = commandChannel
			drainStderr(commandRef)
			val output = readStdout(commandRef, timeoutMs)
			try {
				commandRef.join(2, TimeUnit.SECONDS)
			} catch (_: Exception) {
			}
			val status = commandRef.exitStatus
			if (status != null && status != 0) {
				throw Exception("Command failed with exit status: $status")
			}
			return output.trim()
		} finally {
			try {
				commandChannel?.close()
			} catch (_: Exception) {
			}
			try {
				session.close()
			} catch (_: Exception) {
			}
		}
	}

	fun openShell(cols: Int, rows: Int): SshShellChannel {
		val session = client.startSession()
		try {
			session.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
			val shell = session.startShell()
			return SshShellChannel(shell)
		} catch (e: Exception) {
			try {
				session.close()
			} catch (_: Exception) {
			}
			throw e
		}
	}

	fun openSftp(): SshSftp {
		return SshSftp(client.newSFTPClient())
	}

	private fun drainStderr(command: Session.Command) {
		val error = command.errorStream
		readers.execute {
			try {
				val buffer = ByteArray(1024)
				while (error.read(buffer) >= 0) {
				}
			} catch (_: Exception) {
			}
		}
	}

	private fun readStdout(command: Session.Command, timeoutMs: Long): String {
		val future = readers.submit<String> {
			command.inputStream.bufferedReader(Charsets.UTF_8).readText()
		}
		return try {
			future.get(timeoutMs, TimeUnit.MILLISECONDS)
		} catch (e: TimeoutException) {
			future.cancel(true)
			try {
				command.close()
			} catch (_: Exception) {
			}
			throw Exception("Command execution timeout after ${timeoutMs}ms", e)
		}
	}

	companion object {
		private val readers = Executors.newCachedThreadPool { runnable ->
			Thread(runnable, "ssh-exec").apply { isDaemon = true }
		}

		internal fun openClient(): SSHClient {
			SshSecurity.install()
			val client = SSHClient()
			client.addHostKeyVerifier(PromiscuousVerifier())
			client.connectTimeout = 5_000
			return client
		}

		internal fun passwordFinder(passphrase: String): PasswordFinder {
			return object : PasswordFinder {
				override fun reqPassword(resource: Resource<*>?): CharArray = passphrase.toCharArray()
				override fun shouldRetry(resource: Resource<*>?): Boolean = false
			}
		}
	}
}

class SshShellChannel internal constructor(
	private val shell: Session.Shell
) {
	val inputStream: InputStream
		get() = shell.inputStream

	val outputStream: OutputStream
		get() = shell.outputStream

	fun isOpen(): Boolean {
		return try {
			shell.isOpen
		} catch (_: Exception) {
			false
		}
	}

	fun resize(cols: Int, rows: Int) {
		shell.changeWindowDimensions(cols, rows, 0, 0)
	}

	fun close() {
		try {
			shell.close()
		} catch (_: Exception) {
		}
	}
}

enum class SshSftpCode {
	NO_SUCH_FILE,
	PERMISSION_DENIED,
	FAILURE,
	OTHER
}

class SshSftpException(
	val code: SshSftpCode,
	message: String,
	cause: Throwable? = null
) : Exception(message, cause)

class SshSftpEntry(
	val name: String,
	val isDirectory: Boolean,
	val size: Long
)

class SshSftp internal constructor(
	private val client: SFTPClient
) : AutoCloseable {
	fun pwd(): String = client.canonicalize(".")

	fun statSize(path: String): Long = call { client.stat(path).size }

	fun exists(path: String): Boolean {
		return try {
			client.stat(path)
			true
		} catch (e: SFTPException) {
			if (e.statusCode == Response.StatusCode.NO_SUCH_FILE) false else throw map(e)
		}
	}

	fun list(path: String): List<SshSftpEntry> = call {
		client.ls(path).mapNotNull { info -> info.toEntry() }
	}

	fun put(input: InputStream, remotePath: String, size: Long) = call {
		client.put(
			object : InMemorySourceFile() {
				override fun getName(): String = remotePath.substringAfterLast('/')
				override fun getLength(): Long = size
				override fun getInputStream(): InputStream = input
			},
			remotePath
		)
	}

	fun get(remotePath: String, output: OutputStream) = call {
		client.get(
			remotePath,
			object : InMemoryDestFile() {
				override fun getOutputStream(): OutputStream = output
				override fun getOutputStream(append: Boolean): OutputStream = output
				override fun getLength(): Long = 0L
			}
		)
	}

	fun readBytes(remotePath: String): ByteArray = call {
		val buffer = java.io.ByteArrayOutputStream()
		get(remotePath, buffer)
		buffer.toByteArray()
	}

	fun mkdir(path: String) = call { client.mkdir(path) }

	fun rmdir(path: String) = call { client.rmdir(path) }

	fun rm(path: String) = call { client.rm(path) }

	fun rename(from: String, to: String) = call { client.rename(from, to) }

	override fun close() {
		try {
			client.close()
		} catch (_: Exception) {
		}
	}

	private fun <T> call(block: () -> T): T {
		try {
			return block()
		} catch (e: SshSftpException) {
			throw e
		} catch (e: SFTPException) {
			throw map(e)
		}
	}

	private fun map(e: SFTPException): SshSftpException {
		val code = when (e.statusCode) {
			Response.StatusCode.NO_SUCH_FILE -> SshSftpCode.NO_SUCH_FILE
			Response.StatusCode.PERMISSION_DENIED -> SshSftpCode.PERMISSION_DENIED
			Response.StatusCode.FAILURE -> SshSftpCode.FAILURE
			else -> SshSftpCode.OTHER
		}
		return SshSftpException(code, e.message ?: code.name, e)
	}

	private fun RemoteResourceInfo.toEntry(): SshSftpEntry? {
		val entryName = name
		if (entryName == "." || entryName == "..") return null
		return SshSftpEntry(
			name = entryName,
			isDirectory = isDirectory,
			size = attributes.size
		)
	}
}

internal fun SSHClient.disconnectQuietly() {
	try {
		disconnect()
	} catch (_: IOException) {
	} catch (_: Exception) {
	}
}
