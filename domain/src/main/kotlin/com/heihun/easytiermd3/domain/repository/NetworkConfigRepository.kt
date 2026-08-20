package com.heihun.easytiermd3.domain.repository

import com.heihun.easytiermd3.domain.model.NetworkConfig
import kotlinx.coroutines.flow.Flow

interface NetworkConfigRepository {

    fun observeAll(): Flow<List<NetworkConfig>>

    suspend fun getById(id: String): NetworkConfig?

    suspend fun save(config: NetworkConfig)

    suspend fun delete(id: String)

    suspend fun duplicate(id: String): NetworkConfig

    suspend fun setFavorite(id: String, favorite: Boolean)

    suspend fun setLastUsed(id: String, at: Long)
}
