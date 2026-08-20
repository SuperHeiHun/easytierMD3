package com.heihun.easytiermd3.feature.peer

import androidx.lifecycle.SavedStateHandle
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

@HiltViewModel
class PeerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val peerId: String = savedStateHandle.get<String>("peerId") ?: ""

    data class PeerDetailUiState(
        val peer: EasyTierPeer? = null,
        val connected: Boolean = false,
    )

    private val _uiState = MutableStateFlow(PeerDetailUiState())
    val uiState: StateFlow<PeerDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.peers.collect { peers ->
                _uiState.update {
                    it.copy(peer = peers.firstOrNull { peer -> peer.peerId == peerId })
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                _uiState.update {
                    it.copy(connected = state is EasyTierConnectionState.Running)
                }
            }
        }
    }
}