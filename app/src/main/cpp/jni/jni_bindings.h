#pragma once
// ============================================================================
// jni/jni_bindings.h — native 方法表与入口声明（架构 §2.3 #14/#15 / §5.3）
// ----------------------------------------------------------------------------
// 21 个 JNI 入口（薄层：参数校验 → 句柄解析 → 调 TikenoEngine），签名与
// 架构 §5.3 表格逐一对应。全部为控制面调用（执行循环内零 JNI）。
// ============================================================================

#include <jni.h>

namespace tk {
namespace jni {

// —— 生命周期 ——
jlong nCreate(JNIEnv* env, jclass clazz, jobject engine_config);       // → handle
jint nDestroy(JNIEnv* env, jclass clazz, jlong handle);

// —— 缓冲与 fd 挂载 ——
jint nAttachSeqBuf(JNIEnv* env, jclass clazz, jlong handle, jobject seq_buf);
jint nAttachOutRing(JNIEnv* env, jclass clazz, jlong handle, jobject out_ring);
jint nAttachStatsBuf(JNIEnv* env, jclass clazz, jlong handle, jobject stats_buf);
jint nAttachFds(JNIEnv* env, jclass clazz, jlong handle,
                jobject cmd_fd, jobject out_fd);

// —— 序列装载 ——
jint nLoadSequence(JNIEnv* env, jclass clazz, jlong handle, jint action_count);

// —— 运行控制 ——
jint nStart(JNIEnv* env, jclass clazz, jlong handle);
jint nPause(JNIEnv* env, jclass clazz, jlong handle);
jint nResume(JNIEnv* env, jclass clazz, jlong handle);
jint nStop(JNIEnv* env, jclass clazz, jlong handle);
jint nPostCommand(JNIEnv* env, jclass clazz, jlong handle,
                  jint cmd, jlong arg0, jlong arg1);

// —— 档位与参数 ——
jint nSetTier(JNIEnv* env, jclass clazz, jlong handle, jint tier, jstring device_path);
jint nSetPowerProfile(JNIEnv* env, jclass clazz, jlong handle, jint profile);
jint nSetSpinThreshold(JNIEnv* env, jclass clazz, jlong handle, jlong ns);
jint nSetInterval(JNIEnv* env, jclass clazz, jlong handle, jlong ns);
jint nNotifyResolution(JNIEnv* env, jclass clazz, jlong handle,
                       jint seq, jint x, jint y);

// —— 查询 ——
jint nGetState(JNIEnv* env, jclass clazz, jlong handle);
jint nGetTier(JNIEnv* env, jclass clazz, jlong handle);
jlong nGetMissedTicks(JNIEnv* env, jclass clazz, jlong handle);
jstring nGetBuildInfo(JNIEnv* env, jclass clazz);

// JNINativeMethod 表（tikeno_jni.cpp 的 JNI_OnLoad 使用）
extern const JNINativeMethod kNativeMethods[];
extern const int kNativeMethodCount;

}  // namespace jni
}  // namespace tk
