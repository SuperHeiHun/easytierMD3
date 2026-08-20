package com.heihun.easytiermd3.service

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import com.heihun.easytiermd3.core.bridge.EasyTierCoreManager
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service 层连接状态门面，供 Foreground Service 与通知使用。
 * 统一状态源仍是 EasyTierCoreManager，这里只做转发与附加信息查询。
 */
@Singleton
class ConnectionStateManager @Inject constructor(
    private val coreManager: EasyTierCoreManager,
    private val networkConfigRepository: NetworkConfigRepository,
) {

    val connectionState: StateFlow<EasyTierConnectionState> get() = coreManager.connectionState
    val statistics: StateFlow<EasyTierStatistics> get() = coreManager.statistics
    val peers: StateFlow<List<EasyTierPeer>> get() = coreManager.peers
    val logs: Flow<EasyTierLog> get() = coreManager.logs
    val lastError: StateFlow<String?> get() = coreManager.lastError

    val activeConfig: EasyTierConfig? get() = coreManager.activeConfig

    suspend fun start(config: EasyTierConfig): Result<Unit> = coreManager.start(config)

    suspend fun stop(): Result<Unit> = coreManager.stop()

    suspend fun getTopology(): Result<EasyTierTopology?> = coreManager.getTopology()

    suspend fun networkName(id: String?): String? =
        id?.let { networkConfigRepository.getById(it)?.name }
}