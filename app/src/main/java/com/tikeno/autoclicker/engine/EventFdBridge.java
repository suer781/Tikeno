package com.tikeno.autoclicker.engine;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.tikeno.autoclicker.util.Logx;

/**
 * EventFdBridge — eventfd 包装（架构 §2.4.4 #74）。
 *
 * 用途：Java 侧创建 cmdEfd / outEfd 两个 eventfd 并以 FileDescriptor
 * 交给 C++（nativeAttachFds）；实现全链路跨线程唤醒：
 *   - cmdEfd：Java 写（控制命令到达）→ C++ epoll_wait 唤醒；
 *   - outEfd：C++ 写（outRing 有新步）→ Java MessageQueue fd 监听唤醒。
 *
 * 全部使用 android.system.Os 公开 API（API 23+，minSdk 24 ✓）；
 * 默认 EFD_NONBLOCK：signal 不阻塞（计数饱和时容忍失败，C++ 侧仍能轮询到）。
 */
public final class EventFdBridge implements Closeable {

    private static final String TAG = "Tikeno/Efd";

    private FileDescriptor fd;
    private final ByteBuffer ioBuf = ByteBuffer.allocateDirect(8)
            .order(ByteOrder.LITTLE_ENDIAN);

    private EventFdBridge(FileDescriptor fd) {
        this.fd = fd;
    }

    /** 创建非阻塞 eventfd（初始计数 0）；失败抛 ErrnoException */
    public static EventFdBridge create() throws ErrnoException {
        // EFD_CLOEXEC | EFD_NONBLOCK（与 Linux 语义一致，OsConstants API 23+）
        final int flags = OsConstants.EFD_CLOEXEC | OsConstants.EFD_NONBLOCK;
        FileDescriptor f = Os.eventfd(0, flags);
        return new EventFdBridge(f);
    }

    /** 底层 fd（交给 nativeAttachFds / MessageQueue fd 监听） */
    public FileDescriptor fd() {
        return fd;
    }

    /** 写入计数 1（唤醒对端）；NONBLOCK 下计数饱和会抛 EAGAIN，容忍之 */
    public void signal() {
        try {
            ioBuf.clear();
            ioBuf.putLong(1L);
            ioBuf.flip();
            Os.write(fd, ioBuf);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EAGAIN) {
                Logx.e(TAG, "eventfd signal 失败 errno=" + e.errno);
            }
            // EAGAIN：对端尚未消费，计数已饱和 —— 对端必然已被唤醒，无需处理
        } catch (Exception e) {
            Logx.e(TAG, "eventfd signal 异常", e);
        }
    }

    /** 消费计数（fd 监听回调内调用，清零以便下次唤醒） */
    public void drain() {
        try {
            ioBuf.clear();
            Os.read(fd, ioBuf);   // NONBLOCK：无数据抛 EAGAIN
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EAGAIN) {
                Logx.e(TAG, "eventfd drain 失败 errno=" + e.errno);
            }
        } catch (Exception e) {
            Logx.e(TAG, "eventfd drain 异常", e);
        }
    }

    @Override
    public void close() {
        if (fd != null) {
            try {
                Os.close(fd);
            } catch (ErrnoException e) {
                Logx.w(TAG, "eventfd close 失败 errno=" + e.errno);
            }
            fd = null;
        }
    }

    /** 调试用：API 等级标注（编译期常量） */
    public static int minApiRequired() {
        return Build.VERSION_CODES.M;
    }
}
