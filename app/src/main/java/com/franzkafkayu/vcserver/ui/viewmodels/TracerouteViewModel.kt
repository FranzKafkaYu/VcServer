package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.network.AndroidTtlHopProbe
import com.franzkafkayu.vcserver.network.PhoneHostTraceroute
import com.franzkafkayu.vcserver.network.TraceHop
import com.franzkafkayu.vcserver.network.selectTraceAddress
import com.franzkafkayu.vcserver.network.UnresolvedHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

data class TracerouteUiState(
	val serverName: String,
	val host: String,
	val resolvedAddress: String? = null,
	val hops: List<TraceHop> = emptyList(),
	val running: Boolean = true,
	val resolveFailed: Boolean = false,
	val errorMessage: String? = null,
	val reachedTarget: Boolean = false,
	val hostEchoMs: Long? = null,
	val tcpMs: Long? = null,
	val hopsUnavailable: Boolean = false
)

class TracerouteViewModel(
	serverName: String,
	host: String,
	private val port: Int,
	private val hopProbe: AndroidTtlHopProbe = AndroidTtlHopProbe(),
	private val engine: PhoneHostTraceroute = PhoneHostTraceroute(
		resolver = { name ->
			withContext(Dispatchers.IO) {
				InetAddress.getAllByName(name).toList()
			}
		},
		probeHop = hopProbe::probe
	)
) : ViewModel() {
	private val _uiState = MutableStateFlow(
		TracerouteUiState(serverName = serverName, host = host)
	)
	val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			try {
				val resolved = withContext(Dispatchers.IO) {
					selectTraceAddress(InetAddress.getAllByName(host).toList())
				} ?: throw UnresolvedHostException(host)
				val echo = withContext(Dispatchers.IO) { hopProbe.echoMs(resolved) }
				val tcp = withContext(Dispatchers.IO) { hopProbe.tcpConnectMs(resolved, port) }
				_uiState.update {
					it.copy(
						resolvedAddress = resolved.hostAddress,
						hostEchoMs = echo,
						tcpMs = tcp
					)
				}
				val destAnswersIcmp = echo != null ||
					withContext(Dispatchers.IO) { hopProbe.probe(resolved, 64).reachedTarget }
				if (!destAnswersIcmp) {
					_uiState.update {
						it.copy(running = false, hopsUnavailable = true, hops = emptyList())
					}
					return@launch
				}
				val target = engine.trace(host) { hop ->
					_uiState.update { state ->
						state.copy(
							hops = state.hops + hop,
							reachedTarget = state.reachedTarget || hop.reachedTarget
						)
					}
				}
				_uiState.update {
					it.copy(
						resolvedAddress = target.hostAddress,
						running = false
					)
				}
			} catch (_: UnresolvedHostException) {
				_uiState.update {
					it.copy(running = false, resolveFailed = true, hops = emptyList())
				}
			} catch (_: UnknownHostException) {
				_uiState.update {
					it.copy(running = false, resolveFailed = true, hops = emptyList())
				}
			} catch (e: Exception) {
				_uiState.update {
					it.copy(running = false, errorMessage = e.message, hops = it.hops)
				}
			}
		}
	}

	override fun onCleared() {
		hopProbe.close()
		super.onCleared()
	}
}
