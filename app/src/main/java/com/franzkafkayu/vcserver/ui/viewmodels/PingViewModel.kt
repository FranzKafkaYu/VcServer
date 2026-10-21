package com.franzkafkayu.vcserver.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franzkafkayu.vcserver.network.AndroidTtlHopProbe
import com.franzkafkayu.vcserver.network.HttpCheckHostClient
import com.franzkafkayu.vcserver.network.PhoneHostPing
import com.franzkafkayu.vcserver.network.PingRun
import com.franzkafkayu.vcserver.network.selectTraceAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class PingUiState(
	val serverName: String,
	val host: String,
	val run: PingRun = PingRun()
)

class PingViewModel(
	serverName: String,
	host: String,
	private val probe: AndroidTtlHopProbe = AndroidTtlHopProbe(),
	private val engine: PhoneHostPing = PhoneHostPing(
		resolver = { name ->
			withContext(Dispatchers.IO) {
				selectTraceAddress(InetAddress.getAllByName(name).toList())?.let { listOf(it) }.orEmpty()
			}
		},
		localPing = { address ->
			withContext(Dispatchers.IO) { probe.pingSampleMs(address) }
		},
		remote = HttpCheckHostClient(),
		sleeper = { delay(it) }
	)
) : ViewModel() {
	private val _uiState = MutableStateFlow(PingUiState(serverName = serverName, host = host))
	val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			engine.run(host) { run ->
				_uiState.value = _uiState.value.copy(run = run)
			}
		}
	}

	override fun onCleared() {
		probe.close()
		super.onCleared()
	}
}
