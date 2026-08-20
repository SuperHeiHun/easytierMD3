package com.heihun.easytiermd3.core.api.model

enum class PeerConnectionType {
    DIRECT,
    RELAY,
    UNKNOWN,
}

enum class PeerStatus {
    ONLINE,
    CONNECTING,
    OFFLINE,
}

data class EasyTierPeer(
    val peerId: String,
    val name: String,
    val ipv4: String?,
    val status: PeerStatus,
    val latencyMs: Long?,
    val connectionType: PeerConnectionType,
    val txBytes: Long,
    val rxBytes: Long,
    val lastActiveAt: Long?,
)
