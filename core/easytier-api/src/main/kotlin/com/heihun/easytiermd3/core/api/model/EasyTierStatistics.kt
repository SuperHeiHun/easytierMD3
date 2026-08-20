package com.heihun.easytiermd3.core.api.model

data class EasyTierStatistics(
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
)
