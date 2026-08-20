package com.heihun.easytiermd3.core.api.model

data class TopologyNode(
    val peerId: String,
    val name: String,
    val ipv4: String?,
    val status: PeerStatus,
)

data class TopologyLink(
    val from: String,
    val to: String,
    val latencyMs: Long? = null,
)

data class EasyTierTopology(
    val self: TopologyNode,
    val nodes: List<TopologyNode>,
    val links: List<TopologyLink>,
)
