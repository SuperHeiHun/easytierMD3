package com.heihun.easytiermd3

import androidx.lifecycle.ViewModel
import com.heihun.easytiermd3.domain.model.UserSettings
import com.heihun.easytiermd3.domain.usecase.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsUseCases.settings
}