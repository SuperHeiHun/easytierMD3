package com.heihun.easytiermd3.feature.peer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierPeer
import com.heihun.easytiermd3.core.api.model.PeerConnectionType
import com.heihun.easytiermd3.domain.util.FormatUtils
import com.heihun.easytiermd3.ui.component.EmptyState
import com.heihun.easytiermd3.ui.component.SearchField
import com.heihun.easytiermd3.ui.component.StatusDot
import com.heihun.easytiermd3.ui.component.peerStatusColor
import com.heihun.easytiermd3.ui.component.peerStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListScreen(
    onPeerClick: (String) -> Unit,
    viewModel: PeerListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "节点") },
                actions = {
                    SortMenu(
                        sortMode = uiState.sortMode,
                        onSortModeChange = viewModel::setSortMode,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.connectionState !is EasyTierConnectionState.Running) {
                EmptyState(
                    icon = Icons.Filled.DeviceHub,
                    title = "未连接",
                    message = "连接网络后在此查看节点列表",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                SearchField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = "搜索节点",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (uiState.filteredPeers.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.DeviceHub,
                        title = "没有匹配的节点",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.filteredPeers, key = { it.peerId }) { peer ->
                            PeerCard(peer = peer, onClick = { onPeerClick(peer.peerId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortMode: PeerSortMode,
    onSortModeChange: (PeerSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Filled.Sort, contentDescription = "排序")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        PeerSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(text = mode.label()) },
                onClick = {
                    expanded = false
                    onSortModeChange(mode)
                },
            )
        }
    }
}

private fun PeerSortMode.label(): String = when (this) {
    PeerSortMode.NAME -> "按名称"
    PeerSortMode.LATENCY -> "按延迟"
    PeerSortMode.IPV4 -> "按 IP"
    PeerSortMode.STATUS -> "按状态"
}

@Composable
private fun PeerCard(peer: EasyTierPeer, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = peerStatusColor(peer.status))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = peer.ipv4 ?: "--",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.formatLatency(peer.latencyMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = when (peer.connectionType) {
                        PeerConnectionType.DIRECT -> "Direct"
                        PeerConnectionType.RELAY -> "Relay"
                        PeerConnectionType.UNKNOWN -> "Unknown"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}