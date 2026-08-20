package com.heihun.easytiermd3.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "networks")
data class NetworkEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val config: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val isFavorite: Boolean,
)