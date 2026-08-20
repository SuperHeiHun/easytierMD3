package com.heihun.easytiermd3.core.api.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FakeCore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NativeCore
