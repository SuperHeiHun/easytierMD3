package com.heihun.easytiermd3.domain.usecase

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NetworkConfigUseCases @Inject constructor(
    private val repository: NetworkConfigRepository,
) {

    fun observeAll(): Flow<List<NetworkConfig>> = repository.observeAll()

    suspend fun getById(id: String): NetworkConfig? = repository.getById(id)

    suspend fun save(config: NetworkConfig) = repository.save(config)

    suspend fun delete(id: String) = repository.delete(id)

    suspend fun duplicate(id: String): NetworkConfig = repository.duplicate(id)

    suspend fun setFavorite(id: String, favorite: Boolean) =
        repository.setFavorite(id, favorite)

    suspend fun setLastUsed(id: String, at: Long) = repository.setLastUsed(id, at)

    fun createDraft(): NetworkConfig {
        val now = System.currentTimeMillis()
        val config = EasyTierConfig(
            networkName = "新网络",
            hostname = "Android Phone",
            listenPort = 11010,
        )
        return NetworkConfig(
            id = java.util.UUID.randomUUID().toString(),
            name = "新网络",
            configText = EasyTierConfigCodec.encode(config),
            createdAt = now,
            updatedAt = now,
        )
    }
}
