package com.heihun.easytiermd3.feature.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.model.toDisplayMessage
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.usecase.ConnectNetworkUseCase
import com.heihun.easytiermd3.domain.usecase.DisconnectNetworkUseCase
import com.heihun.easytiermd3.domain.usecase.NetworkConfigUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class NetworkListViewModel @Inject constructor(
    private val networkConfigUseCases: NetworkConfigUseCases,
    private val connectionRepository: ConnectionRepository,
    private val connectUseCase: ConnectNetworkUseCase,
    private val disconnectUseCase: DisconnectNetworkUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class NetworkListUiState(
        val networks: List<NetworkConfig> = emptyList(),
        val activeNetworkId: String? = null,
        val connectionState: EasyTierConnectionState = EasyTierConnectionState.Stopped,
        val peers: List<EasyTierPeer> = emptyList(),
    )

    private val _uiState = MutableStateFlow(NetworkListUiState())
    val uiState: StateFlow<NetworkListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkConfigUseCases.observeAll().collect { list ->
                _uiState.update { it.copy(networks = list) }
            }
        }
        viewModelScope.launch {
            connectionRepository.activeNetworkId.collect { id ->
                _uiState.update { it.copy(activeNetworkId = id) }
            }
        }
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            connectionRepository.peers.collect { peers ->
                _uiState.update { it.copy(peers = peers) }
            }
        }
    }

    fun connect(network: NetworkConfig) {
        viewModelScope.launch {
            val config = EasyTierConfigCodec.decode(network.configText).getOrNull() ?: return@launch
            connectUseCase(network.id, config)
        }
    }

    fun disconnect() {
        viewModelScope.launch { disconnectUseCase() }
    }

    fun delete(network: NetworkConfig) {
        viewModelScope.launch {
            if (_uiState.value.activeNetworkId == network.id) {
                disconnectUseCase()
                withTimeoutOrNull(10_000) {
                    connectionRepository.connectionState.first {
                        it is EasyTierConnectionState.Stopped
                    }
                }
            }
            networkConfigUseCases.delete(network.id)
        }
    }

    fun duplicate(network: NetworkConfig) {
        viewModelScope.launch { networkConfigUseCases.duplicate(network.id) }
    }

    fun toggleFavorite(network: NetworkConfig) {
        viewModelScope.launch {
            networkConfigUseCases.setFavorite(network.id, !network.isFavorite)
        }
    }

    fun readConfigText(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    fun importConfig(text: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = EasyTierConfigCodec.decode(text)
            result.onSuccess { decoded ->
                val now = System.currentTimeMillis()
                networkConfigUseCases.save(
                    NetworkConfig(
                        name = decoded.networkName,
                        configText = text,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                onResult(true, "已导入网络: ${decoded.networkName}")
            }.onFailure { error ->
                val message = (error as? EasyTierConfigCodec.ConfigParseException)
                    ?.toDisplayMessage()
                    ?: error.message
                    ?: "导入失败"
                onResult(false, message)
            }
        }
    }

    fun exportConfig(network: NetworkConfig) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EasyTier 配置: ${network.name}")
            putExtra(Intent.EXTRA_TEXT, network.configText)
        }
        val chooser = Intent.createChooser(sendIntent, "导出配置")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}