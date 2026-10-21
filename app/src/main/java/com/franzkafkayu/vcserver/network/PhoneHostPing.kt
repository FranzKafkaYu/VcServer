package com.franzkafkayu.vcserver.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PingRun(
	val address: String? = null,
	val resolveFailed: Boolean = false,
	val running: Boolean = true,
	val localSamples: List<PingSample> = emptyList(),
	val localUnavailable: Boolean = false,
	val nodes: List<CheckHostNode> = emptyList(),
	val remoteError: String? = null
)

interface CheckHostClient {
	suspend fun start(ip: String): String
	suspend fun result(requestId: String): String
}

class HttpCheckHostClient : CheckHostClient {
	override suspend fun start(ip: String): String = withContext(Dispatchers.IO) {
		val host = URLEncoder.encode(ip, StandardCharsets.UTF_8.name())
		val (code, body) = get("$API/check-ping?host=$host&max_nodes=$MAX_NODES", readTimeoutMs = 15_000)
		val requestId = Regex(""""request_id"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
		logBody("path=check-ping http=$code requestId=${requestId ?: "-"}", body)
		if (code !in 200..299) throw IllegalStateException("http $code")
		body
	}

	override suspend fun result(requestId: String): String = withContext(Dispatchers.IO) {
		val (code, body) = get("$API/check-result/$requestId", readTimeoutMs = 30_000)
		logBody("path=check-result http=$code requestId=$requestId", body)
		if (code !in 200..299) throw IllegalStateException("http $code")
		body
	}

	private fun get(url: String, readTimeoutMs: Int): Pair<Int, String> {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = 15_000
			readTimeout = readTimeoutMs
			setRequestProperty("Accept", "application/json")
			setRequestProperty("Accept-Encoding", "identity")
			setRequestProperty("User-Agent", "VcServer/1.0 (Android; +https://github.com/FranzKafkaYu/VcServer)")
		}
		return try {
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
			code to text
		} finally {
			connection.disconnect()
		}
	}

	private fun logBody(prefix: String, body: String) {
		val chunks = body.chunked(3500).ifEmpty { listOf("") }
		chunks.forEachIndexed { index, chunk ->
			val suffix = if (chunks.size == 1) "" else " part=${index + 1}/${chunks.size}"
			Log.i(TAG, "$prefix$suffix body=$chunk")
		}
	}

	companion object {
		private const val TAG = "CheckHostClient"
		private const val API = "https://check-host.net"
		const val MAX_NODES = 5
	}
}

private fun logRemote(message: String, error: Throwable) {
	try {
		Log.w("CheckHostClient", message, error)
	} catch (_: RuntimeException) {
		// unit tests have no Android log
	}
}

class PhoneHostPing(
	private val resolver: suspend (String) -> List<InetAddress>,
	private val localPing: suspend (InetAddress) -> Long?,
	private val remote: CheckHostClient,
	private val localCount: Int = 4,
	private val pollDelayMs: Long = 2_500,
	private val pollBudgetMs: Long = 90_000,
	private val sleeper: suspend (Long) -> Unit
) {
	suspend fun run(host: String, onUpdate: (PingRun) -> Unit) {
		val target = try {
			selectTraceAddress(resolver(host.trim()))
		} catch (_: Exception) {
			null
		}
		val ip = target?.hostAddress
		if (target == null || ip.isNullOrBlank()) {
			onUpdate(PingRun(resolveFailed = true, running = false))
			return
		}
		val state = RunState(address = ip)
		val gate = Mutex()
		suspend fun publish(running: Boolean) {
			val snapshot = gate.withLock { state.snapshot(running) }
			onUpdate(snapshot)
		}
		publish(running = true)
		coroutineScope {
			val localJob = async {
				try {
					repeat(localCount) {
						val rtt = localPing(target)
						gate.withLock {
							state.localSamples += if (rtt == null) PingSample.Failed else PingSample.Ok(rtt)
						}
						publish(running = true)
					}
				} catch (e: kotlinx.coroutines.CancellationException) {
					throw e
				} catch (_: Exception) {
					gate.withLock { state.localUnavailable = true }
					publish(running = true)
				}
			}
			val remoteJob = async {
				try {
					val request = parseCheckHostRequest(remote.start(ip))
					gate.withLock { state.nodes = request.nodes }
					publish(running = true)
					val deadline = System.currentTimeMillis() + pollBudgetMs
					sleeper(pollDelayMs)
					while (true) {
						try {
							val updated = applyCheckHostResult(
								gate.withLock { state.nodes },
								remote.result(request.requestId)
							)
							val done = gate.withLock {
								state.nodes = updated
								updated.none { it.pending }
							}
							publish(running = true)
							if (done || System.currentTimeMillis() >= deadline) break
						} catch (e: IOException) {
							logRemote("check-result retry after I/O", e)
							if (System.currentTimeMillis() >= deadline) break
						}
						sleeper(pollDelayMs)
					}
				} catch (e: kotlinx.coroutines.CancellationException) {
					throw e
				} catch (e: Exception) {
					logRemote("parse or request failed", e)
					gate.withLock { state.remoteError = e.message ?: "request failed" }
					publish(running = true)
				}
			}
			localJob.await()
			remoteJob.await()
		}
		publish(running = false)
	}

	private class RunState(val address: String) {
		val localSamples = mutableListOf<PingSample>()
		var localUnavailable = false
		var nodes: List<CheckHostNode> = emptyList()
		var remoteError: String? = null

		fun snapshot(running: Boolean) = PingRun(
			address = address,
			running = running,
			localSamples = localSamples.toList(),
			localUnavailable = localUnavailable,
			nodes = nodes,
			remoteError = remoteError
		)
	}
}
