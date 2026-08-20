package com.heihun.easytiermd3.core.native

/**
 * TODO: Phase 4 — 真实 easytier-core Rust JNI 桥接。
 *
 * 本模块将承载编译产物 (libeasytier_jni.so) 与 JNI 封装，
 * 实现 EasyTierCore 接口 (NativeCore)。
 *
 * 严禁臆造 easytier-core API：集成前必须先分析
 * easytier-core 实际源码 / Cargo workspace / 现有 EasyTier Android 项目的
 * 交叉编译与 FFI 方案，再决定 JNI 或 UniFFI。
 */
object RustBridge {

    fun isAvailable(): Boolean = false
}
