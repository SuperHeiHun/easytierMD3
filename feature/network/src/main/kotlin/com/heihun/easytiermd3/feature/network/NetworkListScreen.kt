package com.heihun.easytiermd3.feature.network

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.PeerStatus
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.ui.component.EmptyState
import com.heihun.easytiermd3.ui.component.StatusDot
import com.heihun.easytiermd3.ui.component.connectionStateColor
import com.heihun.easytiermd3.ui.component.peerStatusColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: NetworkListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = viewModel.readConfigText(uri)
            if (text != null) {
                viewModel.importConfig(text) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "我的网络") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }) {
                        Icon(imageVector = Icons.Filled.FileUpload, contentDescription = "导入配置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreate) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "创建网络")
            }
        },
    ) { padding ->
        if (uiState.networks.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Dns,
                title = "还没有网络",
                message = "点击右下角 + 创建你的第一个 EasyTier 网络",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.networks, key = { it.id }) { network ->
                    val isActive = network.id == uiState.activeNetworkId
                    NetworkCard(
                        network = network,
                        isActive = isActive,
                        onlineCount = uiState.peers.count { it.status == PeerStatus.ONLINE },
                        totalPeers = uiState.peers.size,
                        onConnect = { viewModel.connect(network) },
                        onDisconnect = { viewModel.disconnect() },
                        onEdit = { onNavigateToEdit(network.id) },
                        onDuplicate = { viewModel.duplicate(network) },
                        onExport = { viewModel.exportConfig(network) },
                        onDelete = { viewModel.delete(network) },
                        onToggleFavorite = { viewModel.toggleFavorite(network) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkCard(
    network: NetworkConfig,
    isActive: Boolean,
    onlineCount: Int,
    totalPeers: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isActive) onDisconnect() else onConnect() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(
                color = if (isActive) {
                    connectionStateColor(EasyTierConnectionState.Running)
                } else {
                    peerStatusColor(PeerStatus.OFFLINE)
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = network.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (network.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                val subtitle = if (isActive) {
                    "当前运行 · 在线 $onlineCount/$totalPeers"
                } else {
                    "未运行"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多操作")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (isActive) {
                    DropdownMenuItem(
                        text = { Text("断开") },
                        leadingIcon = { Icon(Icons.Filled.Stop, null) },
                        onClick = {
                            menuExpanded = false
                            onDisconnect()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("连接") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = {
                            menuExpanded = false
                            onConnect()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("复制") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("导出") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}