package com.heihun.easytiermd3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.heihun.easytiermd3.data.local.entity.NetworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {

    @Query("SELECT * FROM networks ORDER BY isFavorite DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM networks WHERE id = :id")
    suspend fun getById(id: String): NetworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NetworkEntity)

    @Query("DELETE FROM networks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE networks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE networks SET lastUsedAt = :at WHERE id = :id")
    suspend fun setLastUsed(id: String, at: Long)
}