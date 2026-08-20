package com.heihun.easytiermd3.core.bridge

import com.heihun.easytiermd3.core.api.CoreError
import com.heihun.easytiermd3.core.api.EasyTierCore
import com.heihun.easytiermd3.core.api.EasyTierCoreException
import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.api.model.EasyTierStatus
import com.heihun.easytiermd3.core.api.model.EasyTierTopology
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.core.api.model.PeerConnectionType
import com.heihun.easytiermd3.core.api.model.PeerStatus
import com.heihun.easytiermd3.core.api.model.TopologyLink
import com.heihun.easytiermd3.core.api.model.TopologyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 模拟 EasyTier Core 行为的假实现。
 * Phase 4 由 RustBridge(NativeCore) 替换，接口保持一致。
 */
@Singleton
class FakeEasyTierCore @Inject constructor() : EasyTierCore {

    private val _status = MutableStateFlow(EasyTierStatus.STOPPED)
    private val _peers = MutableStateFlow<List<EasyTierPeer>>(emptyList())
    private val _statistics = MutableStateFlow(EasyTierStatistics())
    private val _lastError = MutableStateFlow<String?>(null)
    private val _logs = MutableSharedFlow<EasyTierLog>(
        replay = 100,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = Random(42)
    private var statisticsJob: Job? = null
    private var peerJob: Job? = null
    private var runningConfig: EasyTierConfig? = null

    init {
        log(LogLevel.INFO, "FakeEasyTierCore 已初始化（TODO: Phase 4 替换为真实 easytier-core）", "core")
    }

    override fun observeStatus(): StateFlow<EasyTierStatus> = _status.asStateFlow()

    override fun observePeers(): StateFlow<List<EasyTierPeer>> = _peers.asStateFlow()

    override fun observeStatistics(): StateFlow<EasyTierStatistics> = _statistics.asStateFlow()

    override fun observeLogs(): Flow<EasyTierLog> = _logs

    override fun observeLastError(): StateFlow<String?> = _lastError.asStateFlow()

    private val _coreVersion = MutableStateFlow<String?>("Fake 0.1.0")

    override fun observeCoreVersion(): StateFlow<String?> = _coreVersion.asStateFlow()

    override suspend fun start(config: EasyTierConfig): Result<Unit> = withContext(Dispatchers.Default) {
        if (config.networkName.isBlank()) {
            val message = "网络名称不能为空"
            return@withContext fail(CoreError.INVALID_CONFIG, message)
        }
        if (config.listenPort !in 1..65535) {
            val message = "监听端口无效: ${config.listenPort}"
            return@withContext fail(CoreError.INVALID_CONFIG, message)
        }
        runningConfig = config
        _lastError.value = null
        _status.value = EasyTierStatus.STARTING
        log(LogLevel.INFO, "正在启动 EasyTier，网络: ${config.networkName} ...", "core")
        delay(1500)
        _status.value = EasyTierStatus.RUNNING
        log(LogLevel.INFO, "EasyTier 已连接网络: ${config.networkName}", "core")
        startStatisticsJob()
        startPeerSimulation()
        Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.Default) {
        _status.value = EasyTierStatus.STOPPING
        log(LogLevel.INFO, "正在停止 EasyTier ...", "core")
        delay(800)
        statisticsJob?.cancel()
        peerJob?.cancel()
        statisticsJob = null
        peerJob = null
        _status.value = EasyTierStatus.STOPPED
        _peers.value = emptyList()
        runningConfig = null
        log(LogLevel.INFO, "EasyTier 已停止", "core")
        Result.success(Unit)
    }

    override suspend fun restart(config: EasyTierConfig): Result<Unit> = withContext(Dispatchers.Default) {
        stop()
        start(config)
    }

    override suspend fun getTopology(): Result<EasyTierTopology?> {
        val config = runningConfig ?: return Result.success(null)
        val self = TopologyNode(
            peerId = "self",
            name = config.hostname ?: "This Device",
            ipv4 = config.ipv4,
            status = PeerStatus.ONLINE,
        )
        val nodes = listOf(self) + _peers.value.map { peer ->
            TopologyNode(
                peerId = peer.peerId,
                name = peer.name,
                ipv4 = peer.ipv4,
                status = peer.status,
            )
        }
        val links = _peers.value.map { peer ->
            TopologyLink(from = self.peerId, to = peer.peerId, latencyMs = peer.latencyMs)
        }
        return Result.success(EasyTierTopology(self = self, nodes = nodes, links = links))
    }

    override suspend fun attachTunFd(fd: Int): Result<Unit> = Result.success(Unit)

    private suspend fun fail(error: CoreError, message: String): Result<Unit> {
        _lastError.value = message
        _status.value = EasyTierStatus.ERROR
        log(LogLevel.ERROR, message, "core")
        return Result.failure(EasyTierCoreException(error, message))
    }

    private fun startStatisticsJob() {
        statisticsJob?.cancel()
        statisticsJob = scope.launch {
            while (isActive) {
                delay(700)
                val upload = (200_000L + random.nextInt(500_000))
                val download = (500_000L + random.nextInt(1_200_000))
                _statistics.update { current ->
                    EasyTierStatistics(
                        uploadBytes = current.uploadBytes + upload * 7 / 10,
                        downloadBytes = current.downloadBytes + download * 7 / 10,
                        uploadSpeed = upload,
                        downloadSpeed = download,
                    )
                }
            }
        }
    }

    private fun startPeerSimulation() {
        peerJob?.cancel()
        peerJob = scope.launch {
            val templates = listOf(
                Triple("peer-pc", "PC-Heihun", "10.144.0.3"),
                Triple("peer-vps", "Singapore VPS", "10.144.0.10"),
                Triple("peer-laptop", "Laptop", "10.144.0.5"),
                Triple("peer-mac", "MacBook", "10.144.0.8"),
            )
            val connecting = templates.mapIndexed { index, (id, name, ip) ->
                EasyTierPeer(
                    peerId = id,
                    name = name,
                    ipv4 = ip,
                    status = PeerStatus.CONNECTING,
                    latencyMs = null,
                    connectionType = if (index == 1) PeerConnectionType.RELAY else PeerConnectionType.DIRECT,
                    txBytes = random.nextInt(50_000_000).toLong(),
                    rxBytes = random.nextInt(200_000_000).toLong(),
                    lastActiveAt = System.currentTimeMillis(),
                )
            }
            _peers.value = connecting
            log(LogLevel.INFO, "发现 ${connecting.size} 个节点，正在连接 ...", "core")
            delay(2000)
            val online = connecting.map { peer ->
                peer.copy(
                    status = PeerStatus.ONLINE,
                    latencyMs = if (peer.connectionType == PeerConnectionType.RELAY) {
                        82L
                    } else {
                        3L + random.nextInt(40)
                    },
                )
            }
            _peers.value = online
            log(LogLevel.INFO, "${online.size} 个节点已连接", "core")
            while (isActive) {
                delay(3000)
                _peers.update { list ->
                    list.map { peer ->
                        val latency = peer.latencyMs?.let {
                            (it + random.nextInt(7) - 3).coerceAtLeast(1L)
                        }
                        peer.copy(
                            latencyMs = latency,
                            txBytes = peer.txBytes + random.nextInt(20_000),
                            rxBytes = peer.rxBytes + random.nextInt(80_000),
                            lastActiveAt = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
    }

    private fun log(level: LogLevel, message: String, tag: String?) {
        _logs.tryEmit(
            EasyTierLog(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                tag = tag,
            )
        )
    }
}
