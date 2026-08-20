package com.heihun.easytiermd3.core.api.model

data class EasyTierConfig(
    val networkName: String,
    val networkSecret: String? = null,
    val hostname: String? = null,
    val startNodes: List<String> = emptyList(),
    val ipv4: String? = null,
    val cidr: String? = null,
    val listenPort: Int = 11010,
    val proxyNetworks: List<ProxyNetworkConfig> = emptyList(),
)
