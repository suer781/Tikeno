// ============================================================================
// platform/affinity.cpp — CPU 亲和性与实时调度实现
// ============================================================================

#include "platform/affinity.h"

#include <sched.h>
#include <unistd.h>
#include <dirent.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

#include "platform/logging.h"

namespace tk {

namespace {

// 读取所有 CPU 核的最大频率（kHz），返回核数；freqs 为输出数组（容量 64）
int read_cpu_max_freqs(unsigned long long* freqs, int max_cpus) {
    DIR* dir = opendir("/sys/devices/system/cpu");
    if (dir == nullptr) return 0;
    int count = 0;
    struct dirent* ent;
    while ((ent = readdir(dir)) != nullptr && count < max_cpus) {
        if (strncmp(ent->d_name, "cpu", 3) != 0) continue;
        // 过滤 cpuidle/cpufreq 等目录，仅要 cpuN
        const char* p = ent->d_name + 3;
        bool all_digit = (*p != '\0');
        for (const char* q = p; *q; ++q) {
            if (*q < '0' || *q > '9') { all_digit = false; break; }
        }
        if (!all_digit) continue;
        char path[128];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/%s/cpufreq/cpuinfo_max_freq", ent->d_name);
        FILE* f = fopen(path, "r");
        if (f == nullptr) {
            freqs[count++] = 0;  // 无 cpufreq 节点（部分模拟器），记 0
            continue;
        }
        unsigned long long khz = 0;
        if (fscanf(f, "%llu", &khz) != 1) khz = 0;
        fclose(f);
        freqs[count++] = khz;
    }
    closedir(dir);
    return count;
}

}  // namespace

bool pin_to_big_core(pthread_t tid) {
    unsigned long long freqs[64];
    const int n = read_cpu_max_freqs(freqs, 64);
    if (n <= 0) return false;

    // 找最大频率（多簇大小核时即"超大核"簇）
    unsigned long long best = 0;
    for (int i = 0; i < n; ++i) {
        if (freqs[i] > best) best = freqs[i];
    }
    if (best == 0) return false;  // 全部无频率信息（如模拟器）

    cpu_set_t set;
    CPU_ZERO(&set);
    int pinned = 0;
    for (int i = 0; i < n; ++i) {
        if (freqs[i] == best) {
            CPU_SET(i, &set);
            ++pinned;
        }
    }
    if (pinned == 0 || pinned == n) return false;  // 全核同频（无大小核），绑定无意义

    if (sched_setaffinity(0, sizeof(set), &set) != 0) {
        TK_LOGD("affinity: sched_setaffinity 失败（保持默认调度）");
        return false;
    }
    TK_LOGD("affinity: 已绑定 %d 个大核（max_freq=%llu kHz）", pinned, best);
    return true;
}

bool try_sched_fifo(pthread_t tid, int priority) {
    struct sched_param param;
    memset(&param, 0, sizeof(param));
    param.sched_priority = priority;
    if (pthread_setschedparam(tid, SCHED_FIFO, &param) != 0) {
        // 非 Root / 无 CAP_SYS_NICE 时必然失败：静默回退（架构 §1.2 难点 2）
        return false;
    }
    TK_LOGD("affinity: SCHED_FIFO(%d) 生效", priority);
    return true;
}

void set_thread_name(pthread_t tid, const char* name) {
    if (name != nullptr) {
        pthread_setname_np(tid, name);
    }
}

}  // namespace tk
