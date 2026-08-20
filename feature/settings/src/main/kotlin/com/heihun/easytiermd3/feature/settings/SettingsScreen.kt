package com.heihun.easytiermd3.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.ui.component.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var themeDialog by remember { mutableStateOf(false) }
    var networkDialog by remember { mutableStateOf(false) }
    var logLevelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "设置") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader(text = "外观") }
            item {
                SettingCard {
                    SettingRow(
                        label = "主题",
                        value = themeModeLabel(uiState.settings.themeMode),
                        onClick = { themeDialog = true },
                    )
                }
            }
            item { SectionHeader(text = "EasyTier") }
            item {
                SettingCard {
                    SettingRow(
                        label = "默认网络",
                        value = uiState.networks.firstOrNull {
                            it.id == uiState.settings.defaultNetworkId
                        }?.name ?: "未设置",
                        onClick = { networkDialog = true },
                    )
                    SettingSwitch(
                        label = "开机自启",
                        description = "TODO: Phase 5 实现 BootReceiver",
                        checked = uiState.settings.autoStart,
                        onCheckedChange = viewModel::setAutoStart,
                    )
                    SettingSwitch(
                        label = "自动连接",
                        description = "应用启动时自动连接默认网络",
                        checked = uiState.settings.autoConnect,
                        onCheckedChange = viewModel::setAutoConnect,
                    )
                }
            }
            item { SectionHeader(text = "网络") }
            item {
                SettingCard {
                    SettingSwitch(
                        label = "Keep Alive",
                        description = "保持后台连接不中断",
                        checked = uiState.settings.keepAlive,
                        onCheckedChange = viewModel::setKeepAlive,
                    )
                    SettingSwitch(
                        label = "自动重连",
                        description = "连接断开后自动重试",
                        checked = uiState.settings.reconnect,
                        onCheckedChange = viewModel::setReconnect,
                    )
                }
            }
            item { SectionHeader(text = "日志") }
            item {
                SettingCard {
                    SettingRow(
                        label = "日志等级",
                        value = uiState.settings.logLevel.name,
                        onClick = { logLevelDialog = true },
                    )
                }
            }
            item { SectionHeader(text = "关于") }
            item {
                SettingCard {
                    SettingRow(label = "版本", value = uiState.versionName)
                    SettingRow(label = "EasyTier Core 版本", value = uiState.coreVersion)
                    SettingRow(label = "Android", value = uiState.androidVersion)
                    SettingRow(label = "设备", value = uiState.deviceModel)
                    SettingRow(
                        label = "项目地址",
                        value = "SuperHeiHun/easytierMD3",
                        onClick = viewModel::openProject,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    )
                    SettingRow(
                        label = "EasyTier 上游",
                        value = "EasyTier/EasyTier",
                        onClick = viewModel::openGitHub,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    )
                }
            }
        }
    }

    if (themeDialog) {
        RadioDialog(
            title = "主题",
            options = ThemeMode.entries.map { it.name to themeModeLabel(it) },
            selected = uiState.settings.themeMode.name,
            onDismiss = { themeDialog = false },
            onSelect = { key ->
                themeDialog = false
                viewModel.setThemeMode(ThemeMode.valueOf(key))
            },
        )
    }

    if (networkDialog) {
        NetworkDialog(
            networks = uiState.networks,
            selectedId = uiState.settings.defaultNetworkId,
            onDismiss = { networkDialog = false },
            onSelect = { id ->
                networkDialog = false
                viewModel.setDefaultNetworkId(id)
            },
        )
    }

    if (logLevelDialog) {
        RadioDialog(
            title = "日志等级",
            options = LogLevel.entries.map { it.name to it.name },
            selected = uiState.settings.logLevel.name,
            onDismiss = { logLevelDialog = false },
            onSelect = { key ->
                logLevelDialog = false
                viewModel.setLogLevel(LogLevel.valueOf(key))
            },
        )
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val baseModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(baseModifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = key == selected,
                            onClick = { onSelect(key) },
                        )
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
    )
}

@Composable
private fun NetworkDialog(
    networks: List<NetworkConfig>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "默认网络") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                    Text(text = "无")
                }
                networks.forEach { network ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = network.id == selectedId,
                            onClick = { onSelect(network.id) },
                        )
                        Text(text = network.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
    )
}