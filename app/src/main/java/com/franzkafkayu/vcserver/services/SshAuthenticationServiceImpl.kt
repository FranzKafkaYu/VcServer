package com.franzkafkayu.vcserver.services

import android.util.Log
import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.ssh.SshClientSession
import com.franzkafkayu.vcserver.ssh.disconnectQuietly
import com.franzkafkayu.vcserver.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectConnection

/**
 * SSH 认证服务实现（sshj）。未知主机密钥按现网策略接受。
 */
class SshAuthenticationServiceImpl(
	@Suppress("unused") private val secureStorage: SecureStorage
) : SshAuthenticationService {
	companion object {
		private const val TAG = "SshAuthenticationService"
		private const val CONNECTION_TIMEOUT = 5_000

		private fun wrapConnectFailure(action: String, e: Exception): SshConnectionException {
			if (isAlgorithmNegotiationFailure(e)) {
				return SshConnectionException("SSH_ALGORITHM_NEGOTIATION_FAILED", e)
			}
			return SshConnectionException("Failed to connect with $action: ${e.message}", e)
		}

		private fun isAlgorithmNegotiationFailure(e: Throwable): Boolean {
			var current: Throwable? = e
			while (current != null) {
				val msg = current.message.orEmpty()
				if (
					msg.contains("Algorithm negotiation fail", ignoreCase = true) ||
					msg.contains("no matching host key", ignoreCase = true) ||
					msg.contains("no matching key exchange", ignoreCase = true) ||
					msg.contains("Unable to negotiate", ignoreCase = true) ||
					msg.contains("no matching", ignoreCase = true)
				) {
					return true
				}
				current = current.cause
			}
			return false
		}
	}

	override suspend fun connectWithPassword(
		host: String,
		port: Int,
		username: String,
		password: String,
		jump: SshClientSession?
	): Result<SshClientSession> = connect(host, port, jump, "password") { client ->
		client.authPassword(username, password)
	}

	override suspend fun connectWithKey(
		host: String,
		port: Int,
		username: String,
		privateKey: String,
		passphrase: String?,
		jump: SshClientSession?
	): Result<SshClientSession> = connect(host, port, jump, "key") { client ->
		val finder = if (!passphrase.isNullOrEmpty()) {
			SshClientSession.passwordFinder(passphrase)
		} else {
			null
		}
		val keys = client.loadKeys(privateKey, null, finder)
		client.authPublickey(username, keys)
	}

	override suspend fun testConnection(
		host: String,
		port: Int,
		username: String,
		authType: AuthType,
		password: String?,
		privateKey: String?,
		passphrase: String?
	): Result<Unit> = withContext(Dispatchers.IO) {
		val result: Result<SshClientSession> = when (authType) {
			AuthType.PASSWORD -> {
				if (password == null) {
					Result.failure(SshConnectionException("Password is required"))
				} else {
					connectWithPassword(host, port, username, password)
				}
			}
			AuthType.KEY -> {
				if (privateKey == null) {
					Result.failure(SshConnectionException("Private key is required"))
				} else {
					connectWithKey(host, port, username, privateKey, passphrase)
				}
			}
		}

		result.fold(
			onSuccess = { session ->
				closeSession(session)
				Result.success(Unit)
			},
			onFailure = { exception ->
				Result.failure(exception)
			}
		)
	}

	override fun closeSession(session: SshClientSession) {
		session.disconnect()
	}

	private suspend fun connect(
		host: String,
		port: Int,
		jump: SshClientSession?,
		action: String,
		authenticate: (SSHClient) -> Unit
	): Result<SshClientSession> = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		val client = SshClientSession.openClient()
		var tunnel: DirectConnection? = null
		try {
			withTimeout(CONNECTION_TIMEOUT.toLong() + 1_000L) {
				runInterruptible {
					if (jump != null) {
						tunnel = jump.openDirect(host, port)
						client.connectVia(tunnel)
					} else {
						client.connect(host, port)
					}
					authenticate(client)
					client.connection.keepAlive.keepAliveInterval = 30
				}
			}
			val duration = System.currentTimeMillis() - startTime
			Log.i(TAG, "connect $action succeed: $host:$port, cost: ${duration}ms")
			Result.success(SshClientSession(client))
		} catch (e: TimeoutCancellationException) {
			client.disconnectQuietly()
			closeTunnel(tunnel)
			Result.failure(SshConnectionException("Failed to connect: timeout", e))
		} catch (e: Exception) {
			val duration = System.currentTimeMillis() - startTime
			Log.i(TAG, "connect $action failed: $host:$port, cost: ${duration}ms, error: ${e.message}")
			client.disconnectQuietly()
			closeTunnel(tunnel)
			Result.failure(wrapConnectFailure(action, e))
		}
	}

	private fun closeTunnel(tunnel: DirectConnection?) {
		try {
			tunnel?.close()
		} catch (_: Exception) {
		}
	}
}

/**
 * SSH 连接异常
 */
class SshConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
