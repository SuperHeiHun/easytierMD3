# EasyTierMD3

基于 **easytier-core** 的现代 Android EasyTier 客户端。

使用 Kotlin + Jetpack Compose + Material Design 3 构建，通过 JNI/FFI 内嵌 easytier-core 2.6.4（arm64-v8a 预编译），内置 VpnService + TUN 数据面，无需 ROOT。

> **AI 辅助开发声明**
>
> 本项目由 **AI（opencode / Claude）辅助开发**：架构设计、代码生成、Bug 排查与修复、真机自动化验证等环节均有 AI 深度参与，并与开发者共同完成。核心工作流为"开发者提出需求与约束 → AI 分析现有代码与 easytier 上游源码 → 增量实现 → 真机验证"。
>
> 所有功能均基于真实 easytier-core 源码能力实现（TOML 扁平 schema、DHCP、Proxy CIDR、TUN fd 挂载等），不使用 Mock/Fake 数据冒充功能。

## 功能特性

- **基础连接**：启动/停止/重启 easytier-core 实例，状态机与错误上报
- **VPN / TUN**：VpnService 建立 TUN 接口，setTunFd 挂载到核心数据面，MTU 1420
- **虚拟 IP**：DHCP 自动分配（`dhcp = true`）或手动静态 IP（`IP/前缀`），IP 变化自动重建 TUN
- **Proxy CIDR（路由）**：发布本机可达网段（含映射网段 mapped_cidr 与放行主机 allow），保存后自动加入 VPN 路由；未知高级 TOML 字段（如 `[flags]`）在普通模式编辑时完整保留
- **节点（Peer）**：在线状态、延迟、直连/中继类型、流量统计
- **网络配置**：多网络管理、4 步创建/编辑向导、导入/导出、复制、收藏
- **高级模式**：直接编辑/校验/恢复原始 EasyTier TOML
- **日志**：核心日志实时查看（5000 条）、级别筛选、复制分享
- **设置**：主题、默认网络、开机自启（待实现 BootReceiver）、自动连接、Keep Alive、日志等级

## 架构

```
Compose UI (home / network / peer / logs / settings)
   ↓ StateFlow
ViewModel
   ↓ UseCase
Repository (Room / DataStore / ConnectionRepository)
   ↓ ServiceStarter (startForegroundService)
EasyTierForegroundService ──► EasyTierVpnService (VpnService.Builder, TUN)
   ↓ ConnectionStateManager
EasyTierCoreManager (Singleton, @NativeCore)
   ↓
NativeEasyTierCore (TOML 生成 + 轮询 collectNetworkInfos)
   ↓
EasyTierJNI (11 个 external 方法)
   ↓
libeasytier_android_jni.so (easytier-android-jni → easytier-ffi → easytier → easytier-core)
```

模块：`:app`、`:core:easytier-api`、`:core:easytier-bridge`、`:core:native`、`:domain`、`:data`、`:service`、`:feature:{home,network,peer,logs,settings}`、`:ui:{theme,component,navigation}`。

## 技术栈

- Kotlin 2.0.21 / AGP 8.7.3 / Gradle 8.14，compileSdk 34 / minSdk 26 / targetSdk 34
- Jetpack Compose + Material 3 + Navigation Compose
- Hilt 2.53.1、Room 2.6.1、DataStore、Coroutines/Flow、Timber
- easytier-core 2.6.4（arm64-v8a 预编译 .so，JNI 桥来自上游 `easytier-contrib/easytier-android-jni`）

## 构建

```bash
# 需要 Android SDK（compileSdk 34）+ JDK 17+
./gradlew assembleDebug
# 或
gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 配置格式

使用 easytier-core 2.x 的扁平 TOML schema：

```toml
instance_name = "easy-tiermd3-xxxxxxxx"
hostname = "Android Phone"
dhcp = true                    # 或 ipv4 = "10.144.0.2/24"
listeners = ["tcp://0.0.0.0:11010", "udp://0.0.0.0:11010"]

[network_identity]
network_name = "My Network"
network_secret = "..."

[[proxy_network]]
cidr = "192.168.1.0/24"
mapped_cidr = "10.233.0.0/24"  # 可选
allow = ["host1"]              # 可选

[[peer]]
uri = "tcp://1.2.3.4:11010"
```

## 路线图

- [x] 基础连接 + VPN/TUN 数据面
- [x] DHCP / 静态虚拟 IP（IP 变化自动重建 TUN）
- [x] Proxy CIDR 路由（配置推导 VPN 路由，TODO: 未来改用 Core 实际路由状态）
- [ ] Listeners / Manual Peer 管理 UI
- [ ] SOCKS5 代理服务
- [ ] Exit Node（Experimental）
- [ ] DNS 配置化
- [ ] 开机自启 BootReceiver
- [ ] Core / VPN / Service 生命周期稳定性重构
- [ ] Android 兼容性与压力测试

## 致谢

- [EasyTier](https://github.com/EasyTier/EasyTier) — 开源 P2P 组网引擎（GPLv3）
- 本项目未使用任何 easytier 官方 Android 客户端代码，JNI 桥直接对接上游 `easytier-contrib/easytier-android-jni`

## 开源许可

本项目基于 [GPLv3](LICENSE) 发布（与 easytier-core 兼容）。