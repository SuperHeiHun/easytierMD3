package com.heihun.easytiermd3.core.api

enum class CoreError {
    CORE_START_FAILED,
    INVALID_CONFIG,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    SERVICE_ERROR,
    UNKNOWN,
}

class EasyTierCoreException(
    val coreError: CoreError,
    message: String,
) : Exception(message)
