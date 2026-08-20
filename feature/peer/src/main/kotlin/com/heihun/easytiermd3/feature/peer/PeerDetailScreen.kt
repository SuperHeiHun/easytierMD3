package com.heihun.easytiermd3.feature.peer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heihun.easytiermd3.core.api.model.PeerConnectionType
import com.heihun.easytiermd3.domain.util.FormatUtils
import com.heihun.easytiermd3.ui.component.EmptyState
import com.heihun.easytiermd3.ui.component.InfoRow
import com.heihun.easytiermd3.ui.component.StatusDot
import com.heihun.easytiermd3.ui.component.peerStatusColor
import com.heihun.easytiermd3.ui.component.peerStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDetailScreen(
    onBack: () -> Unit,
    viewModel: PeerDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "节点详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        val peer = uiState.peer
        if (peer == null || !uiState.connected) {
            EmptyState(
                icon = Icons.Filled.DeviceHub,
                title = "节点不可用",
                message = "节点可能已离线或连接已断开",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(color = peerStatusColor(peer.status))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = peer.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = peerStatusLabel(peer.status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        InfoRow(label = "虚拟 IP", value = peer.ipv4 ?: "--")
                        InfoRow(
                            label = "连接类型",
                            value = when (peer.connectionType) {
                                PeerConnectionType.DIRECT -> "Direct P2P"
                                PeerConnectionType.RELAY -> "Relay"
                                PeerConnectionType.UNKNOWN -> "Unknown"
                            },
                        )
                        InfoRow(
                            label = "延迟",
                            value = FormatUtils.formatLatency(peer.latencyMs),
                        )
                        InfoRow(
                            label = "发送流量",
                            value = FormatUtils.formatBytes(peer.txBytes),
                        )
                        InfoRow(
                            label = "接收流量",
                            value = FormatUtils.formatBytes(peer.rxBytes),
                        )
                        InfoRow(
                            label = "最后活跃",
                            value = FormatUtils.relativeTime(peer.lastActiveAt),
                        )
                        InfoRow(label = "Peer ID", value = peer.peerId)
                    }
                }
                Text(
                    text = "TODO: 实时 Ping / 连接路径 将在接入真实 Core 后提供",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}