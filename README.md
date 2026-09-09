# Tikeno

Android 原生自动连点 / 手势自动化工具。**以运行性能为第一优先级**：延迟敏感核心
（高精度定时、调度循环、轨迹插值、序列引擎）以 **C++17 (NDK/JNI)** 实现，
框架层 **Java**，UI 层 **Kotlin（传统 View + ViewBinding）**。

> 定位：无障碍辅助与 UI 自动化测试工具。不得用于游戏作弊、薅羊毛、抢单等
> 违反第三方协议或法律法规的场景（详见项目内合规声明，T05 轮补全）。

## 构建要求

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Gradle | 8.11.1（wrapper 自动拉取） |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 24（Android 7.0） |
| NDK | **27.0.12077973**（必须，CMake 工具链按其解析） |
| CMake | 3.22.1 |
| Android SDK Build-Tools | 34.0.0 |

SDK 路径配置：复制 `local.properties.sample` 为 `local.properties` 并修改 `sdk.dir`。

## 常用命令

```bash
# Debug 构建（含 NDK/CMake 编译，产物 app/build/outputs/apk/debug/）
./gradlew :app:assembleDebug

# Release 构建（R8 混淆 + 资源收缩 + thin LTO）
./gradlew :app:assembleRelease

# 单元测试 / lint
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

## NDK / Native 说明

- Native 源码位于 `app/src/main/cpp/`，输出 `libtikeno.so`（arm64-v8a / x86_64）。
- Release 编译参数：`-O3 -flto=thin -fno-exceptions -fno-rtti`；Debug：`-O0 -g`。
- 崩溃栈解析：

```bash
adb logcat | ndk-stack -sym app/build/intermediates/cxx/Debug/<hash>/obj/arm64-v8a/
```

- 跨语言数据契约（`TkActionFlat` 64B / `TkPointFlat` 16B / `TkStep` 32B /
  statsBuf 88B）定义在 `app/src/main/cpp/core/types.h`，与 Java 侧
  `ErrorCodes` 等必须同步修改。

## 架构

详见 `docs/02-系统架构设计.md`（分层：Kotlin UI → Java 框架/生命周期 →
JNI 薄层（仅控制面）→ C++ 核心 → 四档注入器）。执行循环内零 JNI 回调、
零 malloc、零锁。

## 合规声明摘要

- 无障碍 API 仅用于在用户主动触发时代替用户重复执行点击/滑动操作；
- 不采集屏幕内容、不上报任何数据；
- 禁止用于破坏任何第三方服务公平性的自动化行为，违规使用后果由用户自行承担。
