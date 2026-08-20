package com.heihun.easytiermd3.core.api

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierStatus
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 统一 Core 访问接口。
 * UI / ViewModel 禁止直接操作 JNI，所有调用必须经过
 * EasyTierCoreManager -> EasyTierCore(桥接实现) -> Rust FFI -> easytier-core。
 */
interface EasyTierCore {

    suspend fun start(config: EasyTierConfig): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun restart(config: EasyTierConfig): Result<Unit>

    fun observeStatus(): StateFlow<EasyTierStatus>

    fun observePeers(): StateFlow<List<EasyTierPeer>>

    fun observeStatistics(): StateFlow<EasyTierStatistics>

    fun observeLogs(): Flow<EasyTierLog>

    suspend fun getTopology(): Result<EasyTierTopology?>

    suspend fun attachTunFd(fd: Int): Result<Unit>

    fun observeLastError(): StateFlow<String?>

    fun observeCoreVersion(): StateFlow<String?>
}
