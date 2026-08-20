package com.heihun.easytiermd3.core.api.model

/**
 * EasyTier Proxy Network（代理网段）配置。
 * 对应 TOML 的 [[proxy_network]] 段：
 *   cidr = "192.168.1.0/24"         本机可达的真实网段
 *   mapped_cidr = "10.233.0.0/24"   向网络广播的映射网段（可选）
 *   allow = ["host1"]               仅放行的主机（可选）
 */
data class ProxyNetworkConfig(
    val cidr: String,
    val mappedCidr: String? = null,
    val allow: List<String> = emptyList(),
)