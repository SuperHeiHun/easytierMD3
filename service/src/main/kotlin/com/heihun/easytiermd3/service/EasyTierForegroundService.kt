package com.heihun.easytiermd3.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.heihun.easytiermd3.core.api.EasyTierIntents
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * EasyTier 后台运行 Foreground Service。
 * Activity / Compose 页面销毁不会停止 Core，只有用户主动断开或
 * Service 被系统停止才会停止 Core。
 */
@AndroidEntryPoint
class EasyTierForegroundService : Service() {

    @Inject lateinit var connectionStateManager: ConnectionStateManager
    @Inject lateinit var networkConfigRepository: NetworkConfigRepository
    @Inject lateinit var notificationHelper: EasyTierNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var proxyCidrs: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        // 立即进入前台，避免 startForegroundService 5 秒超时
        // （断开后快速重连时 onStartCommand 可能延迟到达）
        startForeground(
            EasyTierNotificationHelper.NOTIFICATION_ID,
            notificationHelper.buildNotification(
                EasyTierConnectionState.Starting,
                EasyTierStatistics(),
                null,
            ),
        )
        observeState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            EasyTierIntents.ACTION_START_CORE -> {
                val networkId = intent.getStringExtra(EasyTierIntents.EXTRA_NETWORK_ID)
                startForeground(
                    EasyTierNotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildNotification(
                        EasyTierConnectionState.Starting,
                        EasyTierStatistics(),
                        networkId,
                    ),
                )
                scope.launch { startCore(networkId) }
            }
            EasyTierIntents.ACTION_STOP_CORE -> {
                scope.launch {
                    stopVpnService()
                    proxyCidrs = emptyList()
                    connectionStateManager.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> {
                val current = connectionStateManager.connectionState.value
                startForeground(
                    EasyTierNotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildNotification(
                        current,
                        connectionStateManager.statistics.value,
                        connectionStateManager.activeConfig?.networkName,
                    ),
                )
            }
        }
        return START_STICKY
    }

    private suspend fun startCore(networkId: String?) {
        val network = networkId?.let { networkConfigRepository.getById(it) }
        val config = network?.let { EasyTierConfigCodec.decode(it.configText).getOrNull() }
        if (config == null) {
            Timber.w("EasyTierForegroundService: 未找到可用的网络配置 networkId=%s", networkId)
            stopSelf()
            return
        }
        // TODO: 当前使用配置推导 VPN 路由；未来 EasyTier Core 暴露 Route Table 后改为使用实际路由状态。
        proxyCidrs = config.proxyNetworks.map { it.mappedCidr ?: it.cidr }
        val result = connectionStateManager.start(config)
        if (result.isSuccess) {
            waitForVirtualIpAndStartVpn()
        }
    }

    /**
     * 轮询核心分配的虚拟 IP（DHCP）：IP 稳定后启动 VpnService 建立 TUN 数据面；
     * 核心重新分配 IP 时自动触发 VpnService 重建 TUN。
     */
    private suspend fun waitForVirtualIpAndStartVpn() {
        var lastIp: String? = null
        var stableSince = 0L
        while (true) {
            val state = connectionStateManager.connectionState.value
            if (state is EasyTierConnectionState.Stopped ||
                state is EasyTierConnectionState.Stopping
            ) {
                return
            }
            val ipv4 = connectionStateManager.getTopology()
                .getOrNull()
                ?.self
                ?.ipv4
                ?.takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
            if (ipv4 != null) {
                if (ipv4 != lastIp) {
                    Timber.i("EasyTierForegroundService: 虚拟 IP 更新为 %s", ipv4)
                    lastIp = ipv4
                    stableSince = now
                } else if (now - stableSince >= VPN_IP_STABLE_MS) {
                    stableSince = Long.MAX_VALUE
                    Timber.i("EasyTierForegroundService: 虚拟 IP %s 已稳定，启动/更新 VpnService", ipv4)
                    val intent = Intent(this, EasyTierVpnService::class.java)
                        .putExtra(EasyTierIntents.EXTRA_VPN_IPV4, ipv4)
                    if (proxyCidrs.isNotEmpty()) {
                        intent.putExtra(
                            EasyTierIntents.EXTRA_VPN_PROXY_CIDRS,
                            proxyCidrs.toTypedArray(),
                        )
                    }
                    startService(intent)
                }
            } else {
                lastIp = null
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun stopVpnService() {
        try {
            stopService(Intent(this, EasyTierVpnService::class.java))
        } catch (e: Exception) {
            Timber.w(e, "EasyTierForegroundService: 停止 VpnService 失败")
        }
    }

    private fun observeState() {
        scope.launch {
            combine(
                connectionStateManager.connectionState,
                connectionStateManager.statistics,
            ) { state, stats -> state to stats }.collect { (state, stats) ->
                val name = connectionStateManager.activeConfig?.networkName
                notificationHelper.notify(state, stats, name)
                // 注意：不在状态变化时调用 stopSelf()，避免与
                // 断开后快速重连的 startForegroundService 竞态导致崩溃；
                // 停止统一走 ACTION_STOP_CORE 路径。
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val VPN_IP_STABLE_MS = 3_000L
    }
}