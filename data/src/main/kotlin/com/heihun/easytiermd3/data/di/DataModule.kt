package com.heihun.easytiermd3.data.di

import android.content.Context
import androidx.room.Room
import com.heihun.easytiermd3.data.local.AppDatabase
import com.heihun.easytiermd3.data.local.dao.NetworkDao
import com.heihun.easytiermd3.data.repository.ConnectionRepositoryImpl
import com.heihun.easytiermd3.data.repository.NetworkConfigRepositoryImpl
import com.heihun.easytiermd3.data.repository.SettingsRepositoryImpl
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import com.heihun.easytiermd3.domain.repository.NetworkConfigRepository
import com.heihun.easytiermd3.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindNetworkConfigRepository(
        impl: NetworkConfigRepositoryImpl,
    ): NetworkConfigRepository

    @Binds
    abstract fun bindConnectionRepository(
        impl: ConnectionRepositoryImpl,
    ): ConnectionRepository

    @Binds
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "easytier.db")
                .fallbackToDestructiveMigration()
                .build()

        @Provides
        fun provideNetworkDao(database: AppDatabase): NetworkDao = database.networkDao()
    }
}