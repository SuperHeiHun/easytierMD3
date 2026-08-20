package com.heihun.easytiermd3.domain.repository

import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.domain.model.UserSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val settings: StateFlow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultNetworkId(id: String?)

    suspend fun setAutoStart(enabled: Boolean)

    suspend fun setAutoConnect(enabled: Boolean)

    suspend fun setLogLevel(level: LogLevel)

    suspend fun setKeepAlive(enabled: Boolean)

    suspend fun setReconnect(enabled: Boolean)

    suspend fun setAdvancedMode(enabled: Boolean)
}
