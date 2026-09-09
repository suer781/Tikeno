// Tikeno — 根构建脚本（架构 §2.1 #2）
// AGP 8.7.3 与 Gradle 8.11.1 / JDK 21 / NDK 27 兼容矩阵（架构 §1.1）
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
