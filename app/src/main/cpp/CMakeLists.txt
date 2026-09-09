# ============================================================================
# Tikeno — CMake 构建脚本（架构 §2.3 #12 / §1.1）
# 输出 libtikeno.so；C++17；Release: -O3 -flto=thin -fno-exceptions -fno-rtti
# ============================================================================
cmake_minimum_required(VERSION 3.22.1)
project(tikeno LANGUAGES CXX)

set(TK_VERSION "0.1.0")
add_compile_definitions(TK_VERSION="${TK_VERSION}")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# —— 源文件清单（架构 §2.3 全部 50 个文件中的 .cpp）——
set(TIKENO_SOURCES
        tikeno_jni.cpp
        jni/jni_bindings.cpp
        jni/jni_util.cpp
        core/high_res_timer.cpp
        core/epoll_loop.cpp
        core/shared_ring.cpp
        core/command_queue.cpp
        core/trajectory.cpp
        core/action_builder.cpp
        core/sequence_player.cpp
        core/scheduler.cpp
        core/stats.cpp
        core/power_profile.cpp
        core/engine.cpp
        injection/injector_factory.cpp
        injection/uinput_injector.cpp
        injection/evdev_injector.cpp
        injection/bridge_injector.cpp
        platform/affinity.cpp
        platform/thread.cpp
        )

add_library(tikeno SHARED ${TIKENO_SOURCES})

target_include_directories(tikeno PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# —— 按构建类型设置优化（架构 §1.1 / §2.3 #12）——
target_compile_options(tikeno PRIVATE
        $<$<CONFIG:Release>:-O3>
        $<$<CONFIG:Release>:-flto=thin>
        $<$<CONFIG:Release>:-fno-exceptions>
        $<$<CONFIG:Release>:-fno-rtti>
        $<$<CONFIG:Release>:-DNDEBUG>
        $<$<CONFIG:RelWithDebInfo>:-O3>
        $<$<CONFIG:RelWithDebInfo>:-flto=thin>
        $<$<CONFIG:RelWithDebInfo>:-fno-exceptions>
        $<$<CONFIG:RelWithDebInfo>:-fno-rtti>
        $<$<CONFIG:Debug>:-O0>
        $<$<CONFIG:Debug>:-g>
        )

# LTO 需要链接器侧同样开启 thin LTO
target_link_options(tikeno PRIVATE
        $<$<CONFIG:Release>:-flto=thin>
        $<$<CONFIG:RelWithDebInfo>:-flto=thin>
        )

# —— 链接（log：日志；android：NDK 平台库）——
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(tikeno ${log-lib} ${android-lib})
