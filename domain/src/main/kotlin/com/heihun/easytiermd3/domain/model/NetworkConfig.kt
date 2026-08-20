package com.heihun.easytiermd3.domain.model

import java.util.UUID

data class NetworkConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val configText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long = 0L,
    val isFavorite: Boolean = false,
)
