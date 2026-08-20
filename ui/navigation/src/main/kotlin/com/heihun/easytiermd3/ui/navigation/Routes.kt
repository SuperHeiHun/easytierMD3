package com.heihun.easytiermd3.ui.navigation

object Routes {
    const val HOME = "home"
    const val NETWORKS = "networks"
    const val NETWORK_EDITOR_ARG = "network/editor?networkId={networkId}"
    const val PEERS = "peers"
    const val PEER_DETAIL = "peers/{peerId}"
    const val LOGS = "logs"
    const val SETTINGS = "settings"

    val TOP_LEVEL_ROUTES: Set<String> = setOf(HOME, NETWORKS, PEERS, LOGS, SETTINGS)

    fun networkEditor(networkId: String?): String =
        if (networkId == null) "network/editor" else "network/editor?networkId=$networkId"

    fun peerDetail(peerId: String): String = "peers/$peerId"
}