#pragma once
// ============================================================================
// jni/jni_util.h — JNI RAII 工具（架构 §2.3 #16 / §10.2）
// ----------------------------------------------------------------------------
// - ScopedLocalRef：局部引用 RAII（防控制面泄漏）
// - GetDirectPtr：DirectByteBuffer → 裸地址 + 容量（零拷贝通道的基础）
// - FdFromJavaFileDescriptor：读 FileDescriptor.descriptor（架构 §10.2.4
//   唯一允许的 JNI 反射点；fieldID 缓存一次）
// - 错误处理：C++ 不抛 Java 异常（架构 §10.2.6），统一返回错误码；
//   report_error 仅记日志便于定位。
// ============================================================================

#include <jni.h>
#include <stddef.h>

namespace tk {
namespace jni {

// ---------------------------------------------------------------------------
// 局部引用 RAII
// ---------------------------------------------------------------------------
template <typename T>
class ScopedLocalRef {
 public:
    ScopedLocalRef(JNIEnv* env, T ref) : env_(env), ref_(ref) {}
    ~ScopedLocalRef() {
        if (env_ != nullptr && ref_ != nullptr) {
            env_->DeleteLocalRef(ref_);
        }
    }
    ScopedLocalRef(const ScopedLocalRef&) = delete;
    ScopedLocalRef& operator=(const ScopedLocalRef&) = delete;

    T get() const { return ref_; }
    T release() {
        T tmp = ref_;
        ref_ = nullptr;
        return tmp;
    }

 private:
    JNIEnv* env_;
    T ref_;
};

// ---------------------------------------------------------------------------
// DirectByteBuffer → 裸地址 + 容量
// 非直接缓冲返回 nullptr（Java 侧契约：所有 ByteBuffer 必须 allocateDirect）
// ---------------------------------------------------------------------------
void* get_direct_ptr(JNIEnv* env, jobject buffer, size_t* out_size);

// ---------------------------------------------------------------------------
// FileDescriptor → int fd（反射读 descriptor 字段，fieldID 缓存）
// 失败返回 -1（TK_ERR_CLASS_NOT_FOUND / TK_ERR_FD_READ_FAILED 场景）
// ---------------------------------------------------------------------------
int fd_from_java_file_descriptor(JNIEnv* env, jobject fd_obj);

// ---------------------------------------------------------------------------
// 错误上报（不抛异常，仅日志；架构 §10.2.6）
// ---------------------------------------------------------------------------
void report_error(JNIEnv* env, int error_code, const char* where);

// ---------------------------------------------------------------------------
// JavaVM 缓存（JNI_OnLoad 时设置；供调试断言用）
// ---------------------------------------------------------------------------
void set_cached_vm(JavaVM* vm);
JavaVM* cached_vm();

}  // namespace jni
}  // namespace tk
