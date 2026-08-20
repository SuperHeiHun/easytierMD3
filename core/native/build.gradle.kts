plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.heihun.easytiermd3.core.nativebridge"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:easytier-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
