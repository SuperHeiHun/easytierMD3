package com.heihun.easytiermd3.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "easy_tier_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_NETWORK_ID = stringPreferencesKey("default_network_id")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val RECONNECT = booleanPreferencesKey("reconnect")
        val ADVANCED_MODE = booleanPreferencesKey("advanced_mode")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> toUserSettings(preferences) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultNetworkId(id: String?) {
        context.settingsDataStore.edit {
            if (id == null) it.remove(Keys.DEFAULT_NETWORK_ID) else it[Keys.DEFAULT_NETWORK_ID] = id
        }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_START] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setLogLevel(level: LogLevel) {
        context.settingsDataStore.edit { it[Keys.LOG_LEVEL] = level.name }
    }

    suspend fun setKeepAlive(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KEEP_ALIVE] = enabled }
    }

    suspend fun setReconnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.RECONNECT] = enabled }
    }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ADVANCED_MODE] = enabled }
    }

    private fun toUserSettings(preferences: androidx.datastore.preferences.core.Preferences): UserSettings =
        UserSettings(
            themeMode = preferences[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            defaultNetworkId = preferences[Keys.DEFAULT_NETWORK_ID],
            autoStart = preferences[Keys.AUTO_START] ?: false,
            autoConnect = preferences[Keys.AUTO_CONNECT] ?: false,
            logLevel = preferences[Keys.LOG_LEVEL]
                ?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() }
                ?: LogLevel.INFO,
            keepAlive = preferences[Keys.KEEP_ALIVE] ?: true,
            reconnect = preferences[Keys.RECONNECT] ?: false,
            advancedMode = preferences[Keys.ADVANCED_MODE] ?: false,
        )
}