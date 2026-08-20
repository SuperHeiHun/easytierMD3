package com.heihun.easytiermd3.core.bridge

import com.heihun.easytiermd3.core.api.CoreError
import com.heihun.easytiermd3.core.api.EasyTierCore
import com.heihun.easytiermd3.core.api.EasyTierCoreException
import com.heihun.easytiermd3.core.api.di.NativeCore
import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierStatus
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core 生命周期统一管理者：
 * - 保证 Core 单实例，防止重复启动/重复停止
 * - 将 Core 状态转换为统一的 EasyTierConnectionState
 * - 所有启动/停止/重启操作经过 Mutex 串行化
 */
@Singleton
class EasyTierCoreManager @Inject constructor(
    @NativeCore private val core: EasyTierCore,
) {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState =
        MutableStateFlow<EasyTierConnectionState>(EasyTierConnectionState.Stopped)
    val connectionState: StateFlow<EasyTierConnectionState> = _connectionState.asStateFlow()

    val status: StateFlow<EasyTierStatus> get() = core.observeStatus()
    val peers: StateFlow<List<EasyTierPeer>> get() = core.observePeers()
    val statistics: StateFlow<EasyTierStatistics> get() = core.observeStatistics()
    val logs: Flow<EasyTierLog> get() = core.observeLogs()
    val lastError: StateFlow<String?> get() = core.observeLastError()
    val coreVersion: StateFlow<String?> get() = core.observeCoreVersion()

    var activeConfig: EasyTierConfig? = null
        private set

    val isRunning: Boolean
        get() = _connectionState.value is EasyTierConnectionState.Running

    init {
        scope.launch {
            core.observeStatus().collect { status ->
                _connectionState.value = when (status) {
                    EasyTierStatus.STOPPED -> EasyTierConnectionState.Stopped
                    EasyTierStatus.STARTING -> EasyTierConnectionState.Starting
                    EasyTierStatus.RUNNING -> EasyTierConnectionState.Running
                    EasyTierStatus.STOPPING -> EasyTierConnectionState.Stopping
                    EasyTierStatus.ERROR -> EasyTierConnectionState.Error(
                        core.observeLastError().value ?: "EasyTier 运行异常"
                    )
                }
            }
        }
    }

    suspend fun start(config: EasyTierConfig): Result<Unit> = mutex.withLock {
        val current = _connectionState.value
        if (current is EasyTierConnectionState.Running ||
            current is EasyTierConnectionState.Starting
        ) {
            return@withLock Result.failure(
                EasyTierCoreException(CoreError.CORE_START_FAILED, "EasyTier 已在运行中")
            )
        }
        _connectionState.value = EasyTierConnectionState.Starting
        activeConfig = config
        val result = core.start(config)
        if (result.isFailure) {
            _connectionState.value = EasyTierConnectionState.Error(
                result.exceptionOrNull()?.message ?: "EasyTier 启动失败"
            )
        }
        result
    }

    suspend fun stop(): Result<Unit> = mutex.withLock {
        val current = _connectionState.value
        if (current is EasyTierConnectionState.Stopped) {
            return@withLock Result.success(Unit)
        }
        if (current is EasyTierConnectionState.Error) {
            activeConfig = null
            _connectionState.value = EasyTierConnectionState.Stopped
            return@withLock Result.success(Unit)
        }
        _connectionState.value = EasyTierConnectionState.Stopping
        val result = core.stop()
        if (result.isSuccess) activeConfig = null
        result
    }

    suspend fun restart(config: EasyTierConfig): Result<Unit> {
        stop()
        return start(config)
    }

    suspend fun getTopology(): Result<EasyTierTopology?> = core.getTopology()

    suspend fun attachTunFd(fd: Int): Result<Unit> = core.attachTunFd(fd)
}
