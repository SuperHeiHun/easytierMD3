package com.heihun.easytiermd3.domain.repository

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ConnectionRepository {

    val connectionState: StateFlow<EasyTierConnectionState>

    val peers: StateFlow<List<EasyTierPeer>>

    val statistics: StateFlow<EasyTierStatistics>

    val logs: Flow<EasyTierLog>

    val lastError: StateFlow<String?>

    val coreVersion: StateFlow<String?>

    val activeNetworkId: StateFlow<String?>

    suspend fun connect(networkId: String, config: EasyTierConfig): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    suspend fun restart(config: EasyTierConfig): Result<Unit>

    suspend fun getTopology(): Result<EasyTierTopology?>
}
