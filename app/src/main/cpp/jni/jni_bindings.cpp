// ============================================================================
// jni/jni_bindings.cpp — 21 个 JNI 入口实现（薄层，架构 §5.3）
// ============================================================================

#include "jni/jni_bindings.h"

#include <stdio.h>
#include <string.h>

#include "core/constants.h"
#include "core/engine.h"
#include "core/types.h"
#include "jni/handle_table.h"
#include "jni/jni_util.h"
#include "platform/logging.h"

namespace tk {
namespace jni {

namespace {

constexpr int kOk = static_cast<int>(TkError::kOk);
constexpr int kErrBadHandle = static_cast<int>(TkError::kErrBadHandle);

// 解析句柄；失败时记日志并返回 nullptr
TikenoEngine* resolve(JNIEnv* env, jlong handle, const char* where) {
    TikenoEngine* engine = HandleTable::instance().get(handle);
    if (engine == nullptr) {
        report_error(env, kErrBadHandle, where);
    }
    return engine;
}

}  // namespace

// ---------------------------------------------------------------------------
// 生命周期
// ---------------------------------------------------------------------------
jlong nCreate(JNIEnv* env, jclass /*clazz*/, jobject engine_config) {
    size_t size = 0;
    void* ptr = get_direct_ptr(env, engine_config, &size);
    if (ptr == nullptr || size < sizeof(TkEngineConfigFlat)) {
        report_error(env, static_cast<int>(TkError::kErrNotDirectBuffer), "nCreate");
        return 0;
    }
    TkEngineConfigFlat cfg;
    memcpy(&cfg, ptr, sizeof(cfg));

    TikenoEngine* engine = new TikenoEngine();
    const int rc = engine->create(cfg);
    if (rc != kOk) {
        report_error(env, rc, "nCreate(engine->create)");
        delete engine;
        return 0;
    }
    return HandleTable::instance().put(engine);
}

jint nDestroy(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nDestroy");
    if (engine == nullptr) return kErrBadHandle;
    HandleTable::instance().remove(handle);
    delete engine;  // 析构内部 stop + 释放注入器
    return kOk;
}

// ---------------------------------------------------------------------------
// 缓冲与 fd 挂载
// ---------------------------------------------------------------------------
jint nAttachSeqBuf(JNIEnv* env, jclass /*clazz*/, jlong handle, jobject seq_buf) {
    TikenoEngine* engine = resolve(env, handle, "nAttachSeqBuf");
    if (engine == nullptr) return kErrBadHandle;
    size_t size = 0;
    void* ptr = get_direct_ptr(env, seq_buf, &size);
    if (ptr == nullptr) {
        return static_cast<int>(TkError::kErrNotDirectBuffer);
    }
    return engine->attach_sequence_buffer(ptr, size);
}

jint nAttachOutRing(JNIEnv* env, jclass /*clazz*/, jlong handle, jobject out_ring) {
    TikenoEngine* engine = resolve(env, handle, "nAttachOutRing");
    if (engine == nullptr) return kErrBadHandle;
    size_t size = 0;
    void* ptr = get_direct_ptr(env, out_ring, &size);
    if (ptr == nullptr) {
        return static_cast<int>(TkError::kErrNotDirectBuffer);
    }
    return engine->attach_output_ring(ptr, size);
}

jint nAttachStatsBuf(JNIEnv* env, jclass /*clazz*/, jlong handle, jobject stats_buf) {
    TikenoEngine* engine = resolve(env, handle, "nAttachStatsBuf");
    if (engine == nullptr) return kErrBadHandle;
    size_t size = 0;
    void* ptr = get_direct_ptr(env, stats_buf, &size);
    if (ptr == nullptr) {
        return static_cast<int>(TkError::kErrNotDirectBuffer);
    }
    return engine->attach_stats_buffer(ptr, size);
}

jint nAttachFds(JNIEnv* env, jclass /*clazz*/, jlong handle,
                jobject cmd_fd, jobject out_fd) {
    TikenoEngine* engine = resolve(env, handle, "nAttachFds");
    if (engine == nullptr) return kErrBadHandle;
    // fd 从 FileDescriptor 反射读取（唯一允许的反射点，§10.2.4）
    const int cmd = (cmd_fd != nullptr)
        ? fd_from_java_file_descriptor(env, cmd_fd) : -1;
    const int out = (out_fd != nullptr)
        ? fd_from_java_file_descriptor(env, out_fd) : -1;
    if (cmd_fd != nullptr && cmd < 0) {
        report_error(env, static_cast<int>(TkError::kErrFdReadFailed), "nAttachFds(cmd)");
        return static_cast<int>(TkError::kErrFdReadFailed);
    }
    return engine->attach_fds(cmd, out);
}

// ---------------------------------------------------------------------------
// 序列装载
// ---------------------------------------------------------------------------
jint nLoadSequence(JNIEnv* env, jclass /*clazz*/, jlong handle, jint action_count) {
    TikenoEngine* engine = resolve(env, handle, "nLoadSequence");
    if (engine == nullptr) return kErrBadHandle;
    return engine->load_sequence(action_count);
}

// ---------------------------------------------------------------------------
// 运行控制
// ---------------------------------------------------------------------------
jint nStart(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nStart");
    if (engine == nullptr) return kErrBadHandle;
    return engine->start();
}

jint nPause(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nPause");
    if (engine == nullptr) return kErrBadHandle;
    return engine->pause();
}

jint nResume(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nResume");
    if (engine == nullptr) return kErrBadHandle;
    return engine->resume();
}

jint nStop(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nStop");
    if (engine == nullptr) return kErrBadHandle;
    return engine->stop();
}

jint nPostCommand(JNIEnv* env, jclass /*clazz*/, jlong handle,
                  jint cmd, jlong arg0, jlong arg1) {
    TikenoEngine* engine = resolve(env, handle, "nPostCommand");
    if (engine == nullptr) return kErrBadHandle;
    return engine->post_command(cmd, arg0, arg1);
}

// ---------------------------------------------------------------------------
// 档位与参数
// ---------------------------------------------------------------------------
jint nSetTier(JNIEnv* env, jclass /*clazz*/, jlong handle,
              jint tier, jstring device_path) {
    TikenoEngine* engine = resolve(env, handle, "nSetTier");
    if (engine == nullptr) return kErrBadHandle;
    const char* path = nullptr;
    if (device_path != nullptr) {
        path = env->GetStringUTFChars(device_path, nullptr);
        if (path == nullptr) return static_cast<int>(TkError::kErrInvalidArg);
    }
    const jint rc = engine->set_injector_tier(tier, path);
    if (path != nullptr) {
        env->ReleaseStringUTFChars(device_path, path);
    }
    return rc;
}

jint nSetPowerProfile(JNIEnv* env, jclass /*clazz*/, jlong handle, jint profile) {
    TikenoEngine* engine = resolve(env, handle, "nSetPowerProfile");
    if (engine == nullptr) return kErrBadHandle;
    return engine->set_power_profile(profile);
}

jint nSetSpinThreshold(JNIEnv* env, jclass /*clazz*/, jlong handle, jlong ns) {
    TikenoEngine* engine = resolve(env, handle, "nSetSpinThreshold");
    if (engine == nullptr) return kErrBadHandle;
    return engine->set_spin_threshold_ns(ns);
}

jint nSetInterval(JNIEnv* env, jclass /*clazz*/, jlong handle, jlong ns) {
    TikenoEngine* engine = resolve(env, handle, "nSetInterval");
    if (engine == nullptr) return kErrBadHandle;
    return engine->set_interval_ns(ns);
}

jint nNotifyResolution(JNIEnv* env, jclass /*clazz*/, jlong handle,
                       jint seq, jint x, jint y) {
    // 节点解析回填：经命令队列送到调度线程（kNodeResolved）
    TikenoEngine* engine = resolve(env, handle, "nNotifyResolution");
    if (engine == nullptr) return kErrBadHandle;
    const int64_t packed = (static_cast<int64_t>(y) << 32) |
                           static_cast<uint32_t>(x);
    return engine->post_command(static_cast<int32_t>(TkCommandId::kNodeResolved),
                                seq, packed);
}

// ---------------------------------------------------------------------------
// 查询
// ---------------------------------------------------------------------------
jint nGetState(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nGetState");
    if (engine == nullptr) return kErrBadHandle;
    return engine->state();
}

jint nGetTier(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nGetTier");
    if (engine == nullptr) return kErrBadHandle;
    return engine->tier();
}

jlong nGetMissedTicks(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    TikenoEngine* engine = resolve(env, handle, "nGetMissedTicks");
    if (engine == nullptr) return kErrBadHandle;
    return engine->missed_ticks();
}

jstring nGetBuildInfo(JNIEnv* env, jclass /*clazz*/) {
    // 版本串（MainActivity 骨架验证点：Tikeno/Native: build=...）
    char info[160];
    snprintf(info, sizeof(info),
             "tikeno-native %s"
#if defined(__aarch64__)
             ";abi=arm64-v8a"
#elif defined(__x86_64__)
             ";abi=x86_64"
#elif defined(__arm__)
             ";abi=armeabi-v7a"
#else
             ";abi=unknown"
#endif
             ";ndk=27.0.12077973"
             ";cpp17"
             ";timer=timerfd+nanosleep",
             TK_VERSION);
    return env->NewStringUTF(info);
}

const JNINativeMethod kNativeMethods[] = {
    // Java 方法名            签名                                        C++ 函数
    {"nativeCreate",                 "(Ljava/nio/ByteBuffer;)J",                    (void*)nCreate},
    {"nativeDestroy",                "(J)I",                                        (void*)nDestroy},
    {"nativeAttachSequenceBuffer",   "(JLjava/nio/ByteBuffer;)I",                   (void*)nAttachSeqBuf},
    {"nativeAttachOutputRing",       "(JLjava/nio/ByteBuffer;)I",                   (void*)nAttachOutRing},
    {"nativeAttachStatsBuffer",      "(JLjava/nio/ByteBuffer;)I",                   (void*)nAttachStatsBuf},
    {"nativeAttachFds",              "(JLjava/io/FileDescriptor;Ljava/io/FileDescriptor;)I", (void*)nAttachFds},
    {"nativeLoadSequence",           "(JI)I",                                       (void*)nLoadSequence},
    {"nativeStart",                  "(J)I",                                        (void*)nStart},
    {"nativePause",                  "(J)I",                                        (void*)nPause},
    {"nativeResume",                 "(J)I",                                        (void*)nResume},
    {"nativeStop",                   "(J)I",                                        (void*)nStop},
    {"nativePostCommand",            "(JIJJ)I",                                     (void*)nPostCommand},
    {"nativeSetInjectorTier",        "(JILjava/lang/String;)I",                     (void*)nSetTier},
    {"nativeSetPowerProfile",        "(JI)I",                                       (void*)nSetPowerProfile},
    {"nativeSetSpinThresholdNs",     "(JJ)I",                                       (void*)nSetSpinThreshold},
    {"nativeSetIntervalNs",          "(JJ)I",                                       (void*)nSetInterval},
    {"nativeNotifyResolution",       "(JIII)I",                                     (void*)nNotifyResolution},
    {"nativeGetState",               "(J)I",                                        (void*)nGetState},
    {"nativeGetTier",                "(J)I",                                        (void*)nGetTier},
    {"nativeGetMissedTicks",         "(J)J",                                        (void*)nGetMissedTicks},
    {"nativeGetBuildInfo",           "()Ljava/lang/String;",                        (void*)nGetBuildInfo},
};

const int kNativeMethodCount =
    static_cast<int>(sizeof(kNativeMethods) / sizeof(kNativeMethods[0]));

}  // namespace jni
}  // namespace tk
