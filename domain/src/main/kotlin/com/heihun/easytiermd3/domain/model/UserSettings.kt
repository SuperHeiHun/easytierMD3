package com.heihun.easytiermd3.domain.model

import com.heihun.easytiermd3.core.api.model.LogLevel

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultNetworkId: String? = null,
    val autoStart: Boolean = false,
    val autoConnect: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val keepAlive: Boolean = true,
    val reconnect: Boolean = false,
    val advancedMode: Boolean = false,
)
