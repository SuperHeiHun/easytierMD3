package com.heihun.easytiermd3.core.nativebridge

import com.easytier.jni.EasyTierJNI
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 真实 easytier-core 实现。
 *
 * 通过官方 easytier-android-jni（libeasytier_android_jni.so）在进程内运行
 * easytier-core 实例：
 * - start: runNetworkInstance(扁平 TOML 配置)
 * - 状态/节点/统计: 轮询 collectNetworkInfos（NetworkInstanceRunningInfoMap JSON）
 * - 日志: 轮询 logcat（核心经 android_logger 写入 tag=EasyTier-JNI）
 * - 当前 no_tun=true（无 VpnService 时核心仅管理平面工作，不路由真实流量）
 */
class NativeEasyTierCore : EasyTierCore {

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val LOG_POLL_INTERVAL_MS = 2000L
        private const val LOGCAT_TAG = "EasyTier-JNI"
        private const val CORE_TAG = "core"
        private const val INSTANCE_NAME_PREFIX = "easy-tiermd3-"
        private const val MAX_SEEN_LOG_LINES = 2000
        /** 内嵌 easytier-core 版本（与 jniLibs 中 .so 对应的上游版本一致）。 */
        const val EMBEDDED_CORE_VERSION = "2.6.4"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(EasyTierStatus.STOPPED)
    private val _peers = MutableStateFlow<List<EasyTierPeer>>(emptyList())
    private val _statistics = MutableStateFlow(EasyTierStatistics())
    private val _lastError = MutableStateFlow<String?>(null)
    private val _coreVersion = MutableStateFlow<String?>(EMBEDDED_CORE_VERSION)
    private val _logs = MutableSharedFlow<EasyTierLog>(
        replay = 100,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var pollJob: Job? = null
    private var logJob: Job? = null
    private var instanceName: String? = null
    private var runningConfig: EasyTierConfig? = null
    private var topologyCache: EasyTierTopology? = null
    private var selfPeerId: String? = null
    private var lastTxBytes = 0L
    private var lastRxBytes = 0L
    private var lastSampleTimeMs = 0L
    private val seenLogLines = HashSet<String>()

    override fun observeStatus(): StateFlow<EasyTierStatus> = _status.asStateFlow()

    override fun observePeers(): StateFlow<List<EasyTierPeer>> = _peers.asStateFlow()

    override fun observeStatistics(): StateFlow<EasyTierStatistics> = _statistics.asStateFlow()

    override fun observeLogs(): Flow<EasyTierLog> = _logs

    override fun observeLastError(): StateFlow<String?> = _lastError.asStateFlow()

    override fun observeCoreVersion(): StateFlow<String?> = _coreVersion.asStateFlow()

    override suspend fun start(config: EasyTierConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.networkName.isBlank()) {
            return@withContext fail(CoreError.INVALID_CONFIG, "网络名称不能为空")
        }
        if (config.listenPort !in 1..65535) {
            return@withContext fail(CoreError.INVALID_CONFIG, "监听端口无效: ${config.listenPort}")
        }
        val name = INSTANCE_NAME_PREFIX + UUID.randomUUID().toString().substring(0, 8)
        val toml = encodeToml(config, name)
        try {
            val rc = EasyTierJNI.runNetworkInstance(toml)
            if (rc != 0) {
                val error = EasyTierJNI.getLastError() ?: "启动失败(错误码 $rc)"
                return@withContext fail(CoreError.CORE_START_FAILED, error)
            }
        } catch (e: UnsatisfiedLinkError) {
            return@withContext fail(CoreError.CORE_START_FAILED, "原生库加载失败: ${e.message}")
        } catch (e: RuntimeException) {
            val error = EasyTierJNI.getLastError() ?: e.message ?: "启动失败"
            return@withContext fail(CoreError.CORE_START_FAILED, error)
        }
        instanceName = name
        runningConfig = config
        topologyCache = null
        lastTxBytes = 0L
        lastRxBytes = 0L
        lastSampleTimeMs = 0L
        _lastError.value = null
        _status.value = EasyTierStatus.STARTING
        emitLog(LogLevel.INFO, "EasyTier 核心已启动，网络: ${config.networkName} (instance=$name)", CORE_TAG)
        startPoller()
        Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        _status.value = EasyTierStatus.STOPPING
        stopPoller()
        try {
            EasyTierJNI.retainNetworkInstance(null)
        } catch (_: RuntimeException) {
            // 核心可能已退出，忽略
        }
        instanceName = null
        runningConfig = null
        topologyCache = null
        _peers.value = emptyList()
        _statistics.value = EasyTierStatistics()
        _status.value = EasyTierStatus.STOPPED
        emitLog(LogLevel.INFO, "EasyTier 核心已停止", CORE_TAG)
        Result.success(Unit)
    }

    override suspend fun restart(config: EasyTierConfig): Result<Unit> {
        stop()
        return start(config)
    }

    override suspend fun getTopology(): Result<EasyTierTopology?> = Result.success(topologyCache)

    override suspend fun attachTunFd(fd: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val name = instanceName ?: return@withContext fail(CoreError.CORE_START_FAILED, "实例未启动")
        val rc = try {
            EasyTierJNI.setTunFd(name, fd)
        } catch (e: RuntimeException) {
            val error = EasyTierJNI.getLastError() ?: e.message ?: "setTunFd 失败"
            return@withContext fail(CoreError.CORE_START_FAILED, error)
        }
        if (rc != 0) {
            val error = EasyTierJNI.getLastError() ?: "setTunFd 失败(错误码 $rc)"
            return@withContext fail(CoreError.CORE_START_FAILED, error)
        }
        emitLog(LogLevel.INFO, "TUN 设备已挂载 (fd=$fd)", CORE_TAG)
        Result.success(Unit)
    }

    private suspend fun fail(error: CoreError, message: String): Result<Unit> {
        _lastError.value = message
        _status.value = EasyTierStatus.ERROR
        emitLog(LogLevel.ERROR, message, CORE_TAG)
        return Result.failure(EasyTierCoreException(error, message))
    }

    private fun startPoller() {
        stopPoller()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    pollNetworkInfo()
                } catch (_: Exception) {
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        logJob = scope.launch {
            while (isActive) {
                try {
                    pollLogcat()
                } catch (_: Exception) {
                }
                delay(LOG_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPoller() {
        pollJob?.cancel()
        logJob?.cancel()
        pollJob = null
        logJob = null
    }

    private fun pollNetworkInfo() {
        val name = instanceName ?: return
        val json = EasyTierJNI.collectNetworkInfos(8) ?: return
        val info = JSONObject(json).optJSONObject("map")?.optJSONObject(name) ?: return

        if (!info.optBoolean("running", false)) {
            val error = info.optString("error_msg")
            if (error.isNotBlank()) {
                _lastError.value = error
                _status.value = EasyTierStatus.ERROR
                emitLog(LogLevel.ERROR, error, CORE_TAG)
            } else {
                _status.value = EasyTierStatus.STOPPED
            }
            return
        }

        if (_status.value != EasyTierStatus.RUNNING) {
            _status.value = EasyTierStatus.RUNNING
        }

        val myInfo = info.optJSONObject("my_node_info")
        val myIp = myInfo?.optJSONObject("virtual_ipv4")?.toIpv4InetString()
        selfPeerId = myInfo?.optIntCompat("peer_id")?.toString()
        myInfo?.optString("version")?.takeIf { it.isNotBlank() }?.let {
            if (it != _coreVersion.value) {
                _coreVersion.value = it
            }
        }

        val pairs = info.optJSONArray("peer_route_pairs")
        val peers = buildPeers(pairs ?: info.optJSONArray("peers"))
        _peers.value = peers
        updateStatistics(peers)
        updateTopology(peers, myIp)

        val events = info.optJSONArray("events")
        if (events != null) {
            for (i in 0 until events.length()) {
                val event = events.optString(i).takeIf { it.isNotBlank() } ?: continue
                emitLog(LogLevel.INFO, event, CORE_TAG)
            }
        }
    }

    private fun buildPeers(pairs: JSONArray?): List<EasyTierPeer> {
        if (pairs == null) return emptyList()
        val result = mutableListOf<EasyTierPeer>()
        for (i in 0 until pairs.length()) {
            val pair = pairs.optJSONObject(i) ?: continue
            val route = pair.optJSONObject("route") ?: continue
            val peer = pair.optJSONObject("peer")
            val conns = peer?.optJSONArray("conns") ?: JSONArray()

            val peerId = route.optIntCompat("peer_id")?.toString() ?: continue
            val hostname = route.optString("hostname").takeIf { it.isNotBlank() }
                ?: "peer-$peerId"
            val ipv4 = route.optJSONObject("ipv4_addr")?.toIpv4InetString()
            val nextHop = route.optIntCompat("next_hop_peer_id")

            var active = false
            var latencyUs = -1L
            var tunnelType: String? = null
            var txBytes = 0L
            var rxBytes = 0L
            for (c in 0 until conns.length()) {
                val conn = conns.optJSONObject(c) ?: continue
                if (!conn.optBoolean("is_closed", false)) active = true
                val stats = conn.optJSONObject("stats")
                if (stats != null) {
                    txBytes += stats.optLongCompat("tx_bytes")
                    rxBytes += stats.optLongCompat("rx_bytes")
                    val us = stats.optLongCompat("latency_us")
                    if (us > 0) latencyUs = us
                }
                tunnelType = conn.optJSONObject("tunnel")?.optString("tunnel_type") ?: tunnelType
            }

            val connectionType = when {
                tunnelType == null -> PeerConnectionType.UNKNOWN
                tunnelType == "tcp" || tunnelType == "udp" ||
                    tunnelType == "kcp" || tunnelType == "quic" -> PeerConnectionType.DIRECT
                tunnelType == "wss" || tunnelType == "tls" ||
                    tunnelType == "ws" -> PeerConnectionType.RELAY
                else -> PeerConnectionType.UNKNOWN
            }
            val status = when {
                active -> PeerStatus.ONLINE
                nextHop != null -> PeerStatus.CONNECTING
                else -> PeerStatus.OFFLINE
            }
            result += EasyTierPeer(
                peerId = peerId,
                name = hostname,
                ipv4 = ipv4,
                status = status,
                latencyMs = if (latencyUs > 0) (latencyUs + 500) / 1000 else null,
                connectionType = connectionType,
                txBytes = txBytes,
                rxBytes = rxBytes,
                lastActiveAt = if (active) System.currentTimeMillis() else null,
            )
        }
        return result
    }

    private fun updateStatistics(peers: List<EasyTierPeer>) {
        val tx = peers.sumOf { it.txBytes }
        val rx = peers.sumOf { it.rxBytes }
        val now = System.currentTimeMillis()
        val dt = (now - lastSampleTimeMs).coerceAtLeast(1L)
        val uploadSpeed = if (lastSampleTimeMs > 0) ((tx - lastTxBytes) * 1000 / dt).coerceAtLeast(0L) else 0L
        val downloadSpeed = if (lastSampleTimeMs > 0) ((rx - lastRxBytes) * 1000 / dt).coerceAtLeast(0L) else 0L
        lastTxBytes = tx
        lastRxBytes = rx
        lastSampleTimeMs = now
        _statistics.value = EasyTierStatistics(
            uploadBytes = tx,
            downloadBytes = rx,
            uploadSpeed = uploadSpeed,
            downloadSpeed = downloadSpeed,
        )
    }

    private fun updateTopology(peers: List<EasyTierPeer>, myIp: String?) {
        val config = runningConfig ?: return
        val selfId = selfPeerId ?: "self"
        val self = TopologyNode(
            peerId = selfId,
            name = config.hostname?.takeIf { it.isNotBlank() } ?: "This Device",
            ipv4 = myIp,
            status = PeerStatus.ONLINE,
        )
        val nodes = listOf(self) + peers.map { peer ->
            TopologyNode(
                peerId = peer.peerId,
                name = peer.name,
                ipv4 = peer.ipv4,
                status = peer.status,
            )
        }
        val links = peers.map { peer ->
            TopologyLink(from = selfId, to = peer.peerId, latencyMs = peer.latencyMs)
        }
        topologyCache = EasyTierTopology(self = self, nodes = nodes, links = links)
    }

    private fun pollLogcat() {
        val process = try {
            Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "-s", "$LOGCAT_TAG:V")
            )
        } catch (_: Exception) {
            return
        }
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        for (line in text.lineSequence()) {
            val parsed = parseLogcatLine(line) ?: continue
            if (parsed.isBlank()) continue
            if (!seenLogLines.add(parsed)) continue
            if (seenLogLines.size > MAX_SEEN_LOG_LINES) seenLogLines.clear()
            emitLog(parsedLevel(parsed), parsed, LOGCAT_TAG)
        }
    }

    private fun parseLogcatLine(line: String): String? {
        // threadtime: "MM-DD HH:MM:SS.mmm  PID  TID P TAG : message"
        val sep = line.indexOf(" : ")
        if (sep < 0) return null
        val head = line.substring(0, sep)
        val priorityChar = head.lastOrNull() ?: return null
        if (priorityChar !in "VDIWEF") return null
        return line.substring(sep + 3).trim()
    }

    private fun parsedLevel(line: String): LogLevel {
        // 从解析失败时降级: 消息按内容推断级别
        return when {
            line.startsWith("ERROR") || line.contains("error", ignoreCase = true) -> LogLevel.ERROR
            line.startsWith("WARN") || line.contains("warn", ignoreCase = true) -> LogLevel.WARN
            line.startsWith("DEBUG") || line.contains("debug", ignoreCase = true) -> LogLevel.DEBUG
            line.startsWith("TRACE") || line.contains("trace", ignoreCase = true) -> LogLevel.TRACE
            else -> LogLevel.INFO
        }
    }

    private fun emitLog(level: LogLevel, message: String, tag: String?) {
        _logs.tryEmit(
            EasyTierLog(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                tag = tag,
            )
        )
    }

    /** 生成 easytier-core 当前 TOML（扁平 schema）配置文本。 */
    private fun encodeToml(config: EasyTierConfig, name: String): String = buildString {
        appendLine("instance_name = ${quote(name)}")
        config.hostname?.takeIf { it.isNotBlank() }?.let {
            appendLine("hostname = ${quote(it)}")
        }
        val ipv4 = config.ipv4?.takeIf { it.isNotBlank() }
        if (ipv4 != null) {
            val prefix = config.cidr?.substringAfter('/', "24")?.takeIf { it.isNotBlank() } ?: "24"
            val addr = ipv4.substringBefore('/')
            appendLine("ipv4 = ${quote("$addr/$prefix")}")
        } else {
            appendLine("dhcp = true")
        }
        if (config.listenPort != 0) {
            val port = config.listenPort
            appendLine(
                "listeners = [${quote("tcp://0.0.0.0:$port")}, ${quote("udp://0.0.0.0:$port")}]"
            )
        }
        appendLine()
        appendLine("[network_identity]")
        appendLine("network_name = ${quote(config.networkName)}")
        config.networkSecret?.takeIf { it.isNotBlank() }?.let {
            appendLine("network_secret = ${quote(it)}")
        }
        config.proxyNetworks.forEach { proxy ->
            appendLine()
            appendLine("[[proxy_network]]")
            appendLine("cidr = ${quote(proxy.cidr)}")
            proxy.mappedCidr?.takeIf { it.isNotBlank() }?.let {
                appendLine("mapped_cidr = ${quote(it)}")
            }
            if (proxy.allow.isNotEmpty()) {
                appendLine("allow = [${proxy.allow.joinToString(", ") { quote(it) }}]")
            }
        }
        config.startNodes.filter { it.isNotBlank() }.forEach { uri ->
            appendLine()
            appendLine("[[peer]]")
            appendLine("uri = ${quote(uri)}")
        }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun JSONObject.optIntCompat(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optLongCompat(key: String): Long {
        if (!has(key) || isNull(key)) return 0L
        return when (val v = opt(key)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /** Ipv4Inet JSON {"address":{"addr":int32},"network_length":int} -> "a.b.c.d/prefix" */
    private fun JSONObject.toIpv4InetString(): String? {
        val address = optJSONObject("address") ?: return null
        val addr = address.optLongCompat("addr")
        val networkLength = optIntCompat("network_length") ?: 24
        val ip = listOf(
            (addr shr 24) and 0xFF,
            (addr shr 16) and 0xFF,
            (addr shr 8) and 0xFF,
            addr and 0xFF,
        ).joinToString(".")
        return "$ip/$networkLength"
    }
}