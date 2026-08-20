package com.heihun.easytiermd3.data.repository

import com.heihun.easytiermd3.core.api.model.LogLevel
import com.heihun.easytiermd3.data.datastore.SettingsDataStore
import com.heihun.easytiermd3.domain.model.ThemeMode
import com.heihun.easytiermd3.domain.model.UserSettings
import com.heihun.easytiermd3.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
) : SettingsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val settings: StateFlow<UserSettings> = dataStore.settings
        .catch { emit(UserSettings()) }
        .stateIn(scope, SharingStarted.Eagerly, UserSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)

    override suspend fun setDefaultNetworkId(id: String?) = dataStore.setDefaultNetworkId(id)

    override suspend fun setAutoStart(enabled: Boolean) = dataStore.setAutoStart(enabled)

    override suspend fun setAutoConnect(enabled: Boolean) = dataStore.setAutoConnect(enabled)

    override suspend fun setLogLevel(level: LogLevel) = dataStore.setLogLevel(level)

    override suspend fun setKeepAlive(enabled: Boolean) = dataStore.setKeepAlive(enabled)

    override suspend fun setReconnect(enabled: Boolean) = dataStore.setReconnect(enabled)

    override suspend fun setAdvancedMode(enabled: Boolean) = dataStore.setAdvancedMode(enabled)
}