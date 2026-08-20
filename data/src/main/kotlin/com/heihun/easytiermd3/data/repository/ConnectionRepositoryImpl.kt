package com.heihun.easytiermd3.data.repository

import android.content.Context
import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import com.heihun.easytiermd3.core.bridge.EasyTierCoreManager
import com.heihun.easytiermd3.data.service.ServiceStarter
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val coreManager: EasyTierCoreManager,
    private val networkConfigRepository: NetworkConfigRepository,
    @ApplicationContext private val context: Context,
) : ConnectionRepository {

    override val connectionState: StateFlow<EasyTierConnectionState> =
        coreManager.connectionState

    override val peers: StateFlow<List<EasyTierPeer>> = coreManager.peers

    override val statistics: StateFlow<EasyTierStatistics> = coreManager.statistics

    override val logs: Flow<EasyTierLog> = coreManager.logs

    override val lastError: StateFlow<String?> = coreManager.lastError

    override val coreVersion: StateFlow<String?> = coreManager.coreVersion

    private val _activeNetworkId = MutableStateFlow<String?>(null)
    override val activeNetworkId: StateFlow<String?> = _activeNetworkId.asStateFlow()

    override suspend fun connect(networkId: String, config: EasyTierConfig): Result<Unit> {
        networkConfigRepository.setLastUsed(networkId, System.currentTimeMillis())
        _activeNetworkId.value = networkId
        ServiceStarter.startCore(context, networkId)
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        _activeNetworkId.value = null
        ServiceStarter.stopCore(context)
        return Result.success(Unit)
    }

    override suspend fun restart(config: EasyTierConfig): Result<Unit> =
        coreManager.restart(config)

    override suspend fun getTopology(): Result<EasyTierTopology?> =
        coreManager.getTopology()
}