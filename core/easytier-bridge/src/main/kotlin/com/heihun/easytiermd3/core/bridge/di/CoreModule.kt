package com.heihun.easytiermd3.core.bridge.di

import com.heihun.easytiermd3.core.api.EasyTierCore
import com.heihun.easytiermd3.core.api.di.NativeCore
import com.heihun.easytiermd3.core.nativebridge.NativeEasyTierCore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    @NativeCore
    fun provideNativeCore(): EasyTierCore = NativeEasyTierCore()
}