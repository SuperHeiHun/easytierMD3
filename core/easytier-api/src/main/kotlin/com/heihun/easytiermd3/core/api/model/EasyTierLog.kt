package com.heihun.easytiermd3.core.api.model

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class EasyTierLog(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val tag: String? = null,
)
