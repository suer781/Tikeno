// ============================================================================
// platform/logging.h — 日志宏（架构 §2.3 #43 / §10.3 / §10.4）
// NDEBUG 下 D/V 编译期消除；热路径禁止日志（架构 §10.3）。
// ============================================================================
#pragma once

#include <android/log.h>

#ifdef NDEBUG
#define TK_LOG_TAG "Tikeno/Native"
#define TK_LOGD(...) ((void)0)
#define TK_LOGV(...) ((void)0)
#define TK_LOGI(...) ((void)0)
#define TK_LOGW(...) __android_log_print(ANDROID_LOG_WARN, TK_LOG_TAG, __VA_ARGS__)
#define TK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TK_LOG_TAG, __VA_ARGS__)
#else
#define TK_LOG_TAG "Tikeno/Native"
#define TK_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TK_LOG_TAG, __VA_ARGS__)
#define TK_LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TK_LOG_TAG, __VA_ARGS__)
#define TK_LOGI(...) __android_log_print(ANDROID_LOG_INFO, TK_LOG_TAG, __VA_ARGS__)
#define TK_LOGW(...) __android_log_print(ANDROID_LOG_WARN, TK_LOG_TAG, __VA_ARGS__)
#define TK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TK_LOG_TAG, __VA_ARGS__)
#endif
