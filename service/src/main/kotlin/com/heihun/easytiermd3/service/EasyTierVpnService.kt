package com.heihun.easytiermd3.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.heihun.easytiermd3.core.api.EasyTierIntents
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.core.bridge.EasyTierCoreManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 建立 TUN 虚拟网卡并挂载到 easytier-core。
 *
 * 流程（对齐官方 easytier-android-jni）：
 * 1. 核心已由 EasyTierForegroundService 启动并分配到虚拟 IP（DHCP）
 * 2. 本服务收到虚拟 IP 后建立 VpnService 接口
 * 3. 通过 EasyTierCoreManager.attachTunFd 把 TUN fd 交给核心，数据面开始工作
 */
@AndroidEntryPoint
class EasyTierVpnService : VpnService() {

    @Inject lateinit var coreManager: EasyTierCoreManager
    @Inject lateinit var notificationHelper: EasyTierNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentIpv4: String? = null
    private var currentProxyCidrs: List<String> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ipv4 = intent?.getStringExtra(EasyTierIntents.EXTRA_VPN_IPV4)
        if (ipv4.isNullOrBlank()) {
            Timber.w("EasyTierVpnService: 缺少虚拟 IP")
            stopSelf()
            return START_NOT_STICKY
        }
        currentProxyCidrs = intent
            ?.getStringArrayExtra(EasyTierIntents.EXTRA_VPN_PROXY_CIDRS)
            ?.toList()
            ?: emptyList()
        if (ipv4 == currentIpv4 && vpnInterface != null) {
            return START_STICKY
        }
        startAsForeground()
        scope.launch { setupTun(ipv4) }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = notificationHelper.buildNotification(
            EasyTierConnectionState.Starting,
            EasyTierStatistics(),
            coreManager.activeConfig?.networkName,
        )
        // manifest 中仅声明 foregroundServiceType="vpn"，单参数版本自动使用该类型
        startForeground(EasyTierNotificationHelper.NOTIFICATION_ID, notification)
    }

    private suspend fun setupTun(ipv4: String) = withContext(Dispatchers.IO) {
        try {
            val (ip, prefix) = parseIpv4(ipv4)
            // DHCP 重新分配了虚拟 IP：先释放旧接口再重建
            if (vpnInterface != null) {
                Timber.i("EasyTierVpnService: 虚拟 IP 变化 %s -> %s，重建 TUN", currentIpv4, ipv4)
                vpnInterface?.close()
                vpnInterface = null
            }
            val builder = Builder()
                .setSession("EasyTier")
                .setMtu(DEFAULT_MTU)
                .addAddress(ip, prefix)
                .addDnsServer("223.5.5.5")
                .addDnsServer("114.114.114.114")
                // 排除自身，防止应用流量进入 TUN 造成环路
                .addDisallowedApplication(packageName)

            // addRoute 要求网络地址（主机位必须为 0），且本机网段由 addAddress 隐式添加
            val routes = mutableSetOf<String>()
            routes += "${networkAddress(ip, prefix)}/$prefix"
            coreManager.getTopology().getOrNull()?.nodes?.forEach { node ->
                node.ipv4?.let { peerIpv4 ->
                    try {
                        val (peerIp, peerPrefix) = parseIpv4(peerIpv4)
                        routes += "${networkAddress(peerIp, peerPrefix)}/$peerPrefix"
                    } catch (_: Exception) {
                    }
                }
            }
            routes.forEach { route ->
                val (routeIp, routePrefix) = parseIpv4(route)
                builder.addRoute(routeIp, routePrefix)
                Timber.d("EasyTierVpnService: 添加路由 %s", route)
            }
            // Proxy CIDR 路由：来自网络配置（TODO: 未来改为使用 Core 实际路由状态）。
            // 使用映射网段（mapped_cidr ?: cidr），addRoute 仅接受网络地址。
            currentProxyCidrs.forEach { proxyCidr ->
                try {
                    val (proxyIp, proxyPrefix) = parseIpv4(proxyCidr)
                    val networkIp = networkAddress(proxyIp, proxyPrefix)
                    builder.addRoute(networkIp, proxyPrefix)
                    Timber.d("EasyTierVpnService: 添加代理路由 %s/%s", networkIp, proxyPrefix)
                } catch (e: Exception) {
                    Timber.w("EasyTierVpnService: 跳过无效代理路由 %s: %s", proxyCidr, e.message)
                }
            }

            val pfd = builder.establish()
            if (pfd == null) {
                Timber.e("EasyTierVpnService: 建立 VPN 接口失败（用户未授权？）")
                stopSelf()
                return@withContext
            }
            vpnInterface = pfd
            currentIpv4 = ipv4
            Timber.i("EasyTierVpnService: TUN 建立成功 %s/%s fd=%s", ip, prefix, pfd.fd)

            val result = coreManager.attachTunFd(pfd.fd)
            if (result.isFailure) {
                Timber.e("EasyTierVpnService: 挂载 TUN 失败: %s", result.exceptionOrNull()?.message)
                stopSelf()
            }
        } catch (t: Throwable) {
            Timber.e(t, "EasyTierVpnService: 建立 TUN 异常")
            stopSelf()
        }
    }

    private fun parseIpv4(ipv4: String): Pair<String, Int> {
        return if (ipv4.contains('/')) {
            val parts = ipv4.split('/')
            Pair(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 24)
        } else {
            Pair(ipv4, 24)
        }
    }

    /** 计算 CIDR 网络地址（主机位清零），addRoute 仅接受网络地址。支持 IPv4/IPv6。 */
    private fun networkAddress(ip: String, prefix: Int): String {
        val bytes = java.net.InetAddress.getByName(ip).address
        val maxBits = bytes.size * 8
        val p = prefix.coerceIn(0, maxBits)
        val out = ByteArray(bytes.size) { i ->
            val bitPos = i * 8
            val mask = when {
                p <= bitPos -> 0
                p >= bitPos + 8 -> 0xFF
                else -> (0xFF shl (8 - (p - bitPos))) and 0xFF
            }
            (bytes[i].toInt() and mask).toByte()
        }
        return java.net.InetAddress.getByAddress(out).hostAddress
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_MTU = 1420
    }
}