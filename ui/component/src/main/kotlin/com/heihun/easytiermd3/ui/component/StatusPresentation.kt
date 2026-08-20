package com.heihun.easytiermd3.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.core.api.model.PeerStatus
import com.heihun.easytiermd3.ui.theme.StatusConnecting
import com.heihun.easytiermd3.ui.theme.StatusConnectingDark
import com.heihun.easytiermd3.ui.theme.StatusError
import com.heihun.easytiermd3.ui.theme.StatusErrorDark
import com.heihun.easytiermd3.ui.theme.StatusOffline
import com.heihun.easytiermd3.ui.theme.StatusOfflineDark
import com.heihun.easytiermd3.ui.theme.StatusOnline
import com.heihun.easytiermd3.ui.theme.StatusOnlineDark

@Composable
fun connectionStateTitle(state: EasyTierConnectionState): String = when (state) {
    EasyTierConnectionState.Running -> "已连接"
    EasyTierConnectionState.Starting -> "正在连接"
    EasyTierConnectionState.Stopping -> "正在断开"
    is EasyTierConnectionState.Error -> "连接失败"
    EasyTierConnectionState.Stopped -> "未连接"
}

@Composable
fun connectionStateSubtitle(state: EasyTierConnectionState): String = when (state) {
    EasyTierConnectionState.Running -> "EasyTier 正在运行"
    EasyTierConnectionState.Starting -> "正在启动 EasyTier..."
    EasyTierConnectionState.Stopping -> "正在停止 EasyTier..."
    is EasyTierConnectionState.Error -> state.message
    EasyTierConnectionState.Stopped -> "点击下方按钮开始"
}

@Composable
fun connectionStateColor(state: EasyTierConnectionState): Color {
    val dark = isSystemInDarkTheme()
    return when (state) {
        EasyTierConnectionState.Running -> if (dark) StatusOnlineDark else StatusOnline
        EasyTierConnectionState.Starting,
        EasyTierConnectionState.Stopping,
        -> if (dark) StatusConnectingDark else StatusConnecting
        is EasyTierConnectionState.Error -> if (dark) StatusErrorDark else StatusError
        EasyTierConnectionState.Stopped -> if (dark) StatusOfflineDark else StatusOffline
    }
}

@Composable
fun peerStatusColor(status: PeerStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        PeerStatus.ONLINE -> if (dark) StatusOnlineDark else StatusOnline
        PeerStatus.CONNECTING -> if (dark) StatusConnectingDark else StatusConnecting
        PeerStatus.OFFLINE -> if (dark) StatusOfflineDark else StatusOffline
    }
}

@Composable
fun peerStatusLabel(status: PeerStatus): String = when (status) {
    PeerStatus.ONLINE -> "在线"
    PeerStatus.CONNECTING -> "连接中"
    PeerStatus.OFFLINE -> "离线"
}

@Composable
fun logLevelColor(level: LogLevel): Color {
    val dark = isSystemInDarkTheme()
    return when (level) {
        LogLevel.TRACE, LogLevel.DEBUG -> if (dark) StatusOfflineDark else StatusOffline
        LogLevel.INFO -> if (dark) StatusOnlineDark else StatusOnline
        LogLevel.WARN -> if (dark) StatusConnectingDark else StatusConnecting
        LogLevel.ERROR -> if (dark) StatusErrorDark else StatusError
    }
}