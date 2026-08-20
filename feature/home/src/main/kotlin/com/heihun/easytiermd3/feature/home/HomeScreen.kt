package com.heihun.easytiermd3.feature.home

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.domain.util.FormatUtils
import com.heihun.easytiermd3.ui.component.InfoRow
import com.heihun.easytiermd3.ui.component.StatCard
import com.heihun.easytiermd3.ui.component.connectionStateColor
import com.heihun.easytiermd3.ui.component.connectionStateSubtitle
import com.heihun.easytiermd3.ui.component.connectionStateTitle
import com.heihun.easytiermd3.ui.component.isWindowExpandedWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNetworks: () -> Unit,
    onNavigateToCreateNetwork: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded = isWindowExpandedWidth()
    val context = LocalContext.current
    val vpnAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect()
    }
    val requestConnect: () -> Unit = {
        val intent = VpnService.prepare(context)
        if (intent == null) viewModel.connect() else vpnAuthLauncher.launch(intent)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(title = { Text(text = "EasyTier") })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ConnectionStatusCard(state = uiState.connectionState) }
            item {
                ConnectAction(
                    state = uiState.connectionState,
                    hasNetwork = uiState.defaultNetwork != null,
                    onConnect = requestConnect,
                    onDisconnect = viewModel::disconnect,
                    onCreateNetwork = onNavigateToCreateNetwork,
                )
            }
            if (uiState.connectionState is EasyTierConnectionState.Running ||
                uiState.activeNetwork != null
            ) {
                item {
                    NetworkSummaryCard(
                        networkName = uiState.activeNetwork?.name ?: uiState.defaultNetwork?.name,
                        virtualIp = uiState.virtualIp,
                        onlinePeers = uiState.onlinePeers,
                        totalPeers = uiState.totalPeers,
                        uptimeSeconds = uiState.uptimeSeconds,
                    )
                }
                item {
                    StatisticsRow(statistics = uiState.statistics, expanded = expanded)
                }
            }
            if (uiState.defaultNetwork == null &&
                uiState.connectionState !is EasyTierConnectionState.Running
            ) {
                item {
                    Text(
                        text = "还没有网络配置，点击上方「创建网络」开始使用 EasyTier。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: EasyTierConnectionState) {
    val containerColor = when (state) {
        EasyTierConnectionState.Running -> MaterialTheme.colorScheme.primaryContainer
        is EasyTierConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val animatedColor by animateColorAsState(
        targetValue = containerColor,
        label = "statusCardContainer",
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = animatedColor),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "connectionStatus",
        ) { current ->
            val dotColor = connectionStateColor(current)
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current is EasyTierConnectionState.Starting ||
                    current is EasyTierConnectionState.Stopping
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(color = dotColor, shape = CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = connectionStateTitle(current),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = connectionStateSubtitle(current),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectAction(
    state: EasyTierConnectionState,
    hasNetwork: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCreateNetwork: () -> Unit,
) {
    val running = state is EasyTierConnectionState.Running
    val busy = state is EasyTierConnectionState.Starting ||
        state is EasyTierConnectionState.Stopping

    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(56.dp)

    when {
        running -> {
            Button(
                onClick = onDisconnect,
                modifier = buttonModifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(text = "断开", style = MaterialTheme.typography.titleMedium)
            }
        }
        !hasNetwork -> {
            Button(onClick = onCreateNetwork, modifier = buttonModifier) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "创建网络", style = MaterialTheme.typography.titleMedium)
            }
        }
        else -> {
            Button(
                onClick = onConnect,
                enabled = !busy,
                modifier = buttonModifier,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "正在启动...")
                } else {
                    Text(text = "连接", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun NetworkSummaryCard(
    networkName: String?,
    virtualIp: String?,
    onlinePeers: Int,
    totalPeers: Int,
    uptimeSeconds: Long,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "当前网络",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = networkName ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(label = "虚拟 IP", value = virtualIp ?: "--")
            InfoRow(label = "在线节点", value = "$onlinePeers / $totalPeers")
            InfoRow(label = "运行时间", value = FormatUtils.formatDuration(uptimeSeconds))
        }
    }
}

@Composable
private fun StatisticsRow(
    statistics: EasyTierStatistics,
    expanded: Boolean,
) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                label = "↓ Download",
                value = FormatUtils.formatSpeed(statistics.downloadSpeed),
                icon = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "↑ Upload",
                value = FormatUtils.formatSpeed(statistics.uploadSpeed),
                icon = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "↓ Download",
                value = FormatUtils.formatSpeed(statistics.downloadSpeed),
                icon = Icons.Filled.ArrowDownward,
                modifier = Modifier.fillMaxWidth(),
            )
            StatCard(
                label = "↑ Upload",
                value = FormatUtils.formatSpeed(statistics.uploadSpeed),
                icon = Icons.Filled.ArrowUpward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}