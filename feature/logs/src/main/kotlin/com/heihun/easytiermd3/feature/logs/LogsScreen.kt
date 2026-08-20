package com.heihun.easytiermd3.feature.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.util.FormatUtils
import com.heihun.easytiermd3.ui.component.EmptyState
import com.heihun.easytiermd3.ui.component.SearchField
import com.heihun.easytiermd3.ui.component.logLevelColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.filteredLogs.size) {
        if (uiState.autoScroll && uiState.filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "日志") },
                actions = {
                    IconButton(onClick = viewModel::toggleAutoScroll) {
                        Icon(
                            imageVector = Icons.Filled.VerticalAlignBottom,
                            contentDescription = if (uiState.autoScroll) "暂停自动滚动" else "恢复自动滚动",
                            tint = if (uiState.autoScroll) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    LevelFilterMenu(
                        current = uiState.levelFilter,
                        onSelect = viewModel::setLevelFilter,
                    )
                    IconButton(onClick = viewModel::copyFiltered) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                    IconButton(onClick = viewModel::shareFiltered) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = viewModel::clear) {
                        Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "清空")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "搜索日志",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.filteredLogs.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FilterAlt,
                    title = "没有日志",
                    message = "连接 EasyTier 后这里会实时显示 Core 日志",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(uiState.filteredLogs.reversed()) { log ->
                        LogRow(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterMenu(
    current: LogLevel?,
    onSelect: (LogLevel?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.FilterAlt,
            contentDescription = "级别筛选",
            tint = if (current != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(text = "全部") },
            onClick = {
                expanded = false
                onSelect(null)
            },
        )
        LogLevel.entries.forEach { level ->
            DropdownMenuItem(
                text = { Text(text = level.name) },
                onClick = {
                    expanded = false
                    onSelect(level)
                },
            )
        }
    }
}

@Composable
private fun LogRow(log: EasyTierLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = FormatUtils.formatTimestamp(log.timestamp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = log.level.name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = logLevelColor(log.level),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            val tag = log.tag
            if (tag != null) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}