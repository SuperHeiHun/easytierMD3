package com.heihun.easytiermd3.domain.usecase

import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.domain.model.UserSettings
import com.heihun.easytiermd3.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class SettingsUseCases @Inject constructor(
    private val repository: SettingsRepository,
) {

    val settings: StateFlow<UserSettings> = repository.settings

    suspend fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    suspend fun setDefaultNetworkId(id: String?) = repository.setDefaultNetworkId(id)

    suspend fun setAutoStart(enabled: Boolean) = repository.setAutoStart(enabled)

    suspend fun setAutoConnect(enabled: Boolean) = repository.setAutoConnect(enabled)

    suspend fun setLogLevel(level: LogLevel) = repository.setLogLevel(level)

    suspend fun setKeepAlive(enabled: Boolean) = repository.setKeepAlive(enabled)

    suspend fun setReconnect(enabled: Boolean) = repository.setReconnect(enabled)

    suspend fun setAdvancedMode(enabled: Boolean) = repository.setAdvancedMode(enabled)
}
