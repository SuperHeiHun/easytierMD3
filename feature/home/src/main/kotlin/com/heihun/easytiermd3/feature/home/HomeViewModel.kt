package com.heihun.easytiermd3.feature.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.PeerStatus
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import com.heihun.easytiermd3.domain.usecase.ConnectNetworkUseCase
import com.heihun.easytiermd3.domain.usecase.DisconnectNetworkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    connectionRepository: ConnectionRepository,
    private val networkConfigRepository: NetworkConfigRepository,
    private val connectUseCase: ConnectNetworkUseCase,
    private val disconnectUseCase: DisconnectNetworkUseCase,
) : ViewModel() {

    data class HomeUiState(
        val connectionState: EasyTierConnectionState = EasyTierConnectionState.Stopped,
        val statistics: EasyTierStatistics = EasyTierStatistics(),
        val onlinePeers: Int = 0,
        val totalPeers: Int = 0,
        val activeNetwork: NetworkConfig? = null,
        val defaultNetwork: NetworkConfig? = null,
        val virtualIp: String? = null,
        val uptimeSeconds: Long = 0L,
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var startedAtElapsed: Long? = null

    init {
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                if (state is EasyTierConnectionState.Running && startedAtElapsed == null) {
                    startedAtElapsed = SystemClock.elapsedRealtime()
                } else if (state !is EasyTierConnectionState.Running) {
                    startedAtElapsed = null
                }
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            connectionRepository.statistics.collect { stats ->
                _uiState.update { it.copy(statistics = stats) }
            }
        }
        viewModelScope.launch {
            connectionRepository.peers.collect { peers ->
                _uiState.update {
                    it.copy(
                        onlinePeers = peers.count { peer -> peer.status == PeerStatus.ONLINE },
                        totalPeers = peers.size,
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.activeNetworkId.collect { id -> onActiveNetworkChanged(id) }
        }
        viewModelScope.launch {
            networkConfigRepository.observeAll().collect { list ->
                val activeId = connectionRepository.activeNetworkId.value
                _uiState.update {
                    it.copy(
                        activeNetwork = list.firstOrNull { network -> network.id == activeId },
                        defaultNetwork = list.sortedByDescending { network -> network.lastUsedAt }
                            .firstOrNull(),
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val elapsed = startedAtElapsed
                if (elapsed != null) {
                    _uiState.update {
                        it.copy(uptimeSeconds = (SystemClock.elapsedRealtime() - elapsed) / 1000L)
                    }
                }
                val state = connectionRepository.connectionState.value
                if (state is EasyTierConnectionState.Running) {
                    val selfIp = connectionRepository.getTopology()
                        .getOrNull()
                        ?.self
                        ?.ipv4
                    _uiState.update { it.copy(virtualIp = selfIp ?: it.virtualIp) }
                }
                delay(1000L)
            }
        }
    }

    fun connect() {
        val current = _uiState.value
        if (current.connectionState is EasyTierConnectionState.Running ||
            current.connectionState is EasyTierConnectionState.Starting
        ) {
            return
        }
        val network = current.activeNetwork ?: current.defaultNetwork ?: return
        viewModelScope.launch {
            val config = EasyTierConfigCodec.decode(network.configText).getOrNull() ?: return@launch
            connectUseCase(network.id, config)
        }
    }

    fun disconnect() {
        if (_uiState.value.connectionState is EasyTierConnectionState.Stopped) return
        viewModelScope.launch { disconnectUseCase() }
    }

    private suspend fun onActiveNetworkChanged(id: String?) {
        val network = id?.let { networkConfigRepository.getById(it) }
        val ip = network?.let { EasyTierConfigCodec.decode(it.configText).getOrNull()?.ipv4 }
        _uiState.update { it.copy(activeNetwork = network, virtualIp = ip) }
    }
}