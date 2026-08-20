package com.heihun.easytiermd3.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.domain.model.UserSettings
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.usecase.NetworkConfigUseCases
import com.heihun.easytiermd3.domain.usecase.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsUseCases: SettingsUseCases,
    private val networkConfigUseCases: NetworkConfigUseCases,
    private val connectionRepository: ConnectionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class SettingsUiState(
        val settings: UserSettings = UserSettings(),
        val networks: List<NetworkConfig> = emptyList(),
        val versionName: String = "",
        val coreVersion: String = "未知",
        val androidVersion: String = "",
        val deviceModel: String = "",
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsUseCases.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            networkConfigUseCases.observeAll().collect { networks ->
                _uiState.update { it.copy(networks = networks) }
            }
        }
        viewModelScope.launch {
            connectionRepository.coreVersion.collect { version ->
                _uiState.update { it.copy(coreVersion = version ?: "未知") }
            }
        }
        _uiState.update {
            it.copy(
                versionName = readVersionName(),
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsUseCases.setThemeMode(mode) }
    }

    fun setDefaultNetworkId(id: String?) {
        viewModelScope.launch { settingsUseCases.setDefaultNetworkId(id) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { settingsUseCases.setAutoStart(enabled) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { settingsUseCases.setAutoConnect(enabled) }
    }

    fun setLogLevel(level: LogLevel) {
        viewModelScope.launch { settingsUseCases.setLogLevel(level) }
    }

    fun setKeepAlive(enabled: Boolean) {
        viewModelScope.launch { settingsUseCases.setKeepAlive(enabled) }
    }

    fun setReconnect(enabled: Boolean) {
        viewModelScope.launch { settingsUseCases.setReconnect(enabled) }
    }

    fun openGitHub() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EasyTier/EasyTier"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openProject() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/SuperHeiHun/easytierMD3"),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun readVersionName(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            ).versionName ?: ""
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }
    } catch (e: Exception) {
        ""
    }
}