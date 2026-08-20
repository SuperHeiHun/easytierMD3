package com.heihun.easytiermd3.feature.peer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PeerSortMode {
    NAME,
    LATENCY,
    IPV4,
    STATUS,
}

@HiltViewModel
class PeerListViewModel @Inject constructor(
    connectionRepository: ConnectionRepository,
) : ViewModel() {

    data class PeerListUiState(
        val peers: List<EasyTierPeer> = emptyList(),
        val searchQuery: String = "",
        val sortMode: PeerSortMode = PeerSortMode.NAME,
        val connectionState: EasyTierConnectionState = EasyTierConnectionState.Stopped,
    ) {
        val filteredPeers: List<EasyTierPeer>
            get() {
                val query = searchQuery.trim()
                val filtered = if (query.isEmpty()) {
                    peers
                } else {
                    peers.filter { peer ->
                        peer.name.contains(query, ignoreCase = true) ||
                            peer.ipv4?.contains(query, ignoreCase = true) == true ||
                            peer.peerId.contains(query, ignoreCase = true)
                    }
                }
                return when (sortMode) {
                    PeerSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
                    PeerSortMode.LATENCY -> filtered.sortedWith(
                        compareBy(nullsLast()) { it.latencyMs }
                    )
                    PeerSortMode.IPV4 -> filtered.sortedBy { it.ipv4 ?: "" }
                    PeerSortMode.STATUS -> filtered.sortedBy { it.status.ordinal }
                }
            }
    }

    private val _uiState = MutableStateFlow(PeerListUiState())
    val uiState: StateFlow<PeerListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.peers.collect { peers ->
                _uiState.update { it.copy(peers = peers) }
            }
        }
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortMode(mode: PeerSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }
}