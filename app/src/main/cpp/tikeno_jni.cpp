// ============================================================================
// tikeno_jni.cpp — JNI_OnLoad / JNI_OnUnload（架构 §2.3 #13 / §5.1）
// ----------------------------------------------------------------------------
// RegisterNatives 静态注册（与 Java 方法名解耦，ProGuard 重命名不打断链接）；
// 缓存 JavaVM；JNI_OnUnload 确定性清理。
// 版本串验证点：MainActivity 显示 nativeGetBuildInfo() 返回值。
// ============================================================================

#include <jni.h>

#include "core/constants.h"
#include "jni/jni_bindings.h"
#include "jni/jni_util.h"
#include "platform/logging.h"

using tk::jni::kNativeMethods;
using tk::jni::kNativeMethodCount;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK ||
        env == nullptr) {
        TK_LOGE("JNI_OnLoad: GetEnv 失败");
        return JNI_ERR;
    }
    tk::jni::set_cached_vm(vm);

    // 找到 NativeEngine 类并注册全部 native 方法
    jclass clazz = env->FindClass("com/tikeno/autoclicker/jni/NativeEngine");
    if (clazz == nullptr) {
        env->ExceptionClear();
        TK_LOGE("JNI_OnLoad: NativeEngine 类未找到");
        return JNI_ERR;
    }
    if (env->RegisterNatives(clazz, kNativeMethods, kNativeMethodCount) != JNI_OK) {
        env->DeleteLocalRef(clazz);
        env->ExceptionClear();
        TK_LOGE("JNI_OnLoad: RegisterNatives 失败（%d 个方法）", kNativeMethodCount);
        return JNI_ERR;
    }
    env->DeleteLocalRef(clazz);

    TK_LOGI("Tikeno/Native: build=%s — %d 个 native 方法注册完成",
            TK_VERSION, kNativeMethodCount);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    // 架构 §5.1：确定性清理。C++ 侧仅缓存 JavaVM（无 jobject 全局引用，
    // 无 jclass 弱引用——RegisterNatives 的类引用由 JVM 管理），无需释放。
    // HandleTable 为进程级静态（引擎全部 destroy 后自然为空）。
    tk::jni::set_cached_vm(nullptr);
}
