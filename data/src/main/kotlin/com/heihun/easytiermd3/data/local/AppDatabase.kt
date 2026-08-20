package com.heihun.easytiermd3.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.heihun.easytiermd3.data.local.dao.NetworkDao
import com.heihun.easytiermd3.data.local.entity.NetworkEntity

@Database(
    entities = [NetworkEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
}