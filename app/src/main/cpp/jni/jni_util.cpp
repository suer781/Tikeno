// ============================================================================
// jni/jni_util.cpp — JNI RAII 工具实现
// ============================================================================

#include "jni/jni_util.h"

#include <pthread.h>
#include <string.h>

#include "core/types.h"
#include "platform/logging.h"

namespace tk {
namespace jni {

namespace {

// FileDescriptor.descriptor fieldID 缓存（进程级一次；控制面读，非热路径）
pthread_once_t g_fd_field_once = PTHREAD_ONCE_INIT;
jfieldID g_fd_descriptor_field = nullptr;

void init_fd_field_id() {
    // 缓存依赖 JNIEnv，但 pthread_once 无参——用进程首次调用时环境传播：
    // JNI_OnLoad 已缓存 JavaVM，这里通过 Attach 临时获取（控制面一次性成本）
    JavaVM* vm = cached_vm();
    if (vm == nullptr) return;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK ||
        env == nullptr) {
        return;
    }
    ScopedLocalRef<jclass> cls(env, env->FindClass("java/io/FileDescriptor"));
    if (cls.get() == nullptr) {
        env->ExceptionClear();
        return;
    }
    g_fd_descriptor_field = env->GetFieldID(cls.get(), "descriptor", "I");
    if (g_fd_descriptor_field == nullptr) {
        env->ExceptionClear();
        TK_LOGE("jni_util: FileDescriptor.descriptor 字段未找到");
    }
}

JavaVM* g_vm = nullptr;

}  // namespace

void set_cached_vm(JavaVM* vm) {
    g_vm = vm;
}

JavaVM* cached_vm() {
    return g_vm;
}

void* get_direct_ptr(JNIEnv* env, jobject buffer, size_t* out_size) {
    if (env == nullptr || buffer == nullptr) return nullptr;
    void* addr = env->GetDirectBufferAddress(buffer);
    if (addr == nullptr) {
        // 非直接缓冲（契约违约）：调用方返回 TK_ERR_NOT_DIRECT_BUFFER
        return nullptr;
    }
    if (out_size != nullptr) {
        const jlong cap = env->GetDirectBufferCapacity(buffer);
        *out_size = (cap > 0) ? static_cast<size_t>(cap) : 0;
    }
    return addr;
}

int fd_from_java_file_descriptor(JNIEnv* env, jobject fd_obj) {
    if (env == nullptr || fd_obj == nullptr) return -1;
    pthread_once(&g_fd_field_once, init_fd_field_id);
    if (g_fd_descriptor_field == nullptr) {
        // 二次尝试（VM 缓存晚于首次调用的极端时序）
        init_fd_field_id();
        if (g_fd_descriptor_field == nullptr) return -1;
    }
    return env->GetIntField(fd_obj, g_fd_descriptor_field);
}

void report_error(JNIEnv* /*env*/, int error_code, const char* where) {
    // 架构 §10.2.6：C++ 不抛 Java 异常，一律返回错误码；
    // 此处仅记日志帮助定位（UI 文案由 Java ErrorCodes.toString 映射）
    TK_LOGE("JNI 错误 code=%d at %s", error_code, where != nullptr ? where : "?");
}

}  // namespace jni
}  // namespace tk
