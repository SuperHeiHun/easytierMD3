package com.heihun.easytiermd3.data.repository

import com.heihun.easytiermd3.data.local.dao.NetworkDao
import com.heihun.easytiermd3.data.local.entity.NetworkEntity
import com.heihun.easytiermd3.domain.model.NetworkConfig
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConfigRepositoryImpl @Inject constructor(
    private val dao: NetworkDao,
) : NetworkConfigRepository {

    override fun observeAll(): Flow<List<NetworkConfig>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): NetworkConfig? =
        dao.getById(id)?.toDomain()

    override suspend fun save(config: NetworkConfig) =
        dao.insert(config.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun duplicate(id: String): NetworkConfig {
        val source = getById(id)
            ?: throw IllegalArgumentException("网络配置不存在: $id")
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (副本)",
            createdAt = now,
            updatedAt = now,
            lastUsedAt = 0L,
            isFavorite = false,
        )
        dao.insert(copy.toEntity())
        return copy
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) =
        dao.setFavorite(id, favorite)

    override suspend fun setLastUsed(id: String, at: Long) =
        dao.setLastUsed(id, at)

    private fun NetworkEntity.toDomain() = NetworkConfig(
        id = id,
        name = name,
        configText = config,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        isFavorite = isFavorite,
    )

    private fun NetworkConfig.toEntity() = NetworkEntity(
        id = id,
        name = name,
        config = configText,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        isFavorite = isFavorite,
    )
}