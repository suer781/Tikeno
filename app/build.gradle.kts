// Tikeno — app 模块构建脚本（架构 §2.2 #9 / §9 依赖清单）
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tikeno.autoclicker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tikeno.autoclicker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a 为主力 ABI；x86_64 保留用于模拟器调试（架构 §1.1）
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // c++_static：单 so 自包含 STL，避免 libc++_shared 依赖
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            // 中文路径兼容（Windows）：SDK CMake 3.22.1 处理中文 -B 构建目录参数时
            // 崩溃（exit 127，二分法已验证；-H 源码目录为中文无影响）。
            // 本机开发可通过 -PtikenoCxxDir=<ASCII 路径> 将 .cxx 暂存目录重定向；
            // 未设置该属性时保持默认 app/.cxx，其他环境不受影响。
            (findProperty("tikenoCxxDir") as? String)?.let {
                buildStagingDirectory = file(it)
            }
        }
    }

    ndkVersion = "27.0.12077973"

    buildTypes {
        release {
            // Release：混淆 + 资源收缩（架构 §2.2 #9）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug：不混淆，保留符号便于 ndk-stack（架构 §1.1）
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    // —— 按架构 §9.2 清单，版本为 Maven 可解析的稳定版 ——
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // 架构原定 1.13.0（不存在稳定版），降级到 1.12.0 —— 偏差已记录
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // L2 Shell 档（T03 起使用，先锁定版本）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
