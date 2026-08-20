package com.heihun.easytiermd3.core.api.model

sealed interface EasyTierConnectionState {
    data object Stopped : EasyTierConnectionState
    data object Starting : EasyTierConnectionState
    data object Running : EasyTierConnectionState
    data object Stopping : EasyTierConnectionState
    data class Error(val message: String) : EasyTierConnectionState
}
