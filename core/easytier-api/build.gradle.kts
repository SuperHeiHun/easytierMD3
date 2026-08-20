plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
