package com.heihun.easytiermd3.feature.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.EasyTierLog
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    connectionRepository: ConnectionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class LogsUiState(
        val logs: List<EasyTierLog> = emptyList(),
        val searchQuery: String = "",
        val levelFilter: LogLevel? = null,
        val autoScroll: Boolean = true,
    ) {
        val filteredLogs: List<EasyTierLog>
            get() {
                val query = searchQuery.trim()
                return logs.filter { log ->
                    (levelFilter == null || log.level.ordinal >= levelFilter.ordinal) &&
                        (query.isEmpty() ||
                            log.message.contains(query, ignoreCase = true) ||
                            log.tag?.contains(query, ignoreCase = true) == true)
                }
            }
    }

    private val _logs = MutableStateFlow<List<EasyTierLog>>(emptyList())
    private val _uiState = MutableStateFlow(LogsUiState(logs = _logs.value))
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.logs.collect { log ->
                _logs.update { current ->
                    (current + log).takeLast(MAX_LOGS)
                }
                _uiState.update { it.copy(logs = _logs.value) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setLevelFilter(level: LogLevel?) {
        _uiState.update { it.copy(levelFilter = level) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun clear() {
        _logs.value = emptyList()
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun copyFiltered() {
        val text = formatLogs(_uiState.value.filteredLogs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyTier 日志", text))
    }

    fun shareFiltered() {
        val text = formatLogs(_uiState.value.filteredLogs)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EasyTier 日志")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "分享日志")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun formatLogs(logs: List<EasyTierLog>): String = buildString {
        logs.forEach { log ->
            appendLine(
                "${FormatUtils.formatTimestamp(log.timestamp)} " +
                    "[${log.level.name}] " +
                    "${log.tag?.let { "[$it] " } ?: ""}" +
                    log.message
            )
        }
    }

    companion object {
        const val MAX_LOGS = 5000
    }
}