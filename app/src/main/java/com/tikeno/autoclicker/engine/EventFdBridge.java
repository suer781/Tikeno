package com.tikeno.autoclicker.engine;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.tikeno.autoclicker.util.Logx;

/**
 * EventFdBridge — 跨线程唤醒通道包装（架构 §2.4.4 #74）。
 *
 * 用途：Java 侧创建 cmdFd / outFd 两个唤醒管道并以 FileDescriptor 交给
 * C++（nativeAttachFds）；实现全链路跨线程唤醒：
 *   - cmdFd：Java 写（控制命令到达）→ C++ epoll_wait 唤醒；
 *   - outFd：C++ 写（outRing 有新步）→ Java MessageQueue fd 监听唤醒。
 *
 * 【实现说明】理想后端是 Linux eventfd，但 public Os API 未暴露
 * （OsConstants.EFD_* / Os.eventfd 在 compileSdk 35 编译面上不存在），
 * 故回退到 Os.pipe()（pipe2）：fd 语义对 C++ epoll/Java fd 监听完全等价。
 * 协议约定 8 字节对齐：signal 写 8B 计数（eventfd 计数语义），
 * drain 读 8B —— 与 C++ 侧 read(fd,&u64,8) 的 eventfd 用法兼容。
 * 全部使用 android.system.Os 公开 API（API 21+）。
 */
public final class EventFdBridge implements Closeable {

    private static final String TAG = "Tikeno/Efd";

    private FileDescriptor localFd;   // 本端 fd（cmd=写端 signal / out=读端 drain+监听）
    private FileDescriptor peerFd;    // 对端 fd（交 nativeAttachFds；C++ 读 cmd / 写 out）
    private final ByteBuffer ioBuf = ByteBuffer.allocateDirect(8)
            .order(ByteOrder.LITTLE_ENDIAN);

    private EventFdBridge(FileDescriptor localFd, FileDescriptor peerFd) {
        this.localFd = localFd;
        this.peerFd = peerFd;
    }

    /**
     * 创建唤醒通道。
     *  - iAmWriter=true（cmdFd）：本端=pipe 写端（Java signal），对端=读端（交 C++）；
     *  - iAmWriter=false（outFd）：本端=pipe 读端（Java drain/监听），对端=写端（交 C++）。
     */
    public static EventFdBridge create(boolean iAmWriter) throws ErrnoException {
        final FileDescriptor[] pipe = Os.pipe();
        return new EventFdBridge(
                iAmWriter ? pipe[1] : pipe[0],
                iAmWriter ? pipe[0] : pipe[1]);
    }

    /** 交给 nativeAttachFds 的对端 FileDescriptor */
    public FileDescriptor fd() {
        return peerFd;
    }

    /** 本端 FileDescriptor（MessageQueue fd 监听用；仅读端有意义） */
    public FileDescriptor localFd() {
        return localFd;
    }

    /** 写端角色：唤醒 C++（每次写 8 字节计数 1，与 eventfd 写 u64 语义对齐） */
    public void signal() {
        try {
            ioBuf.clear();
            ioBuf.putLong(1L);
            ioBuf.flip();
            Os.write(localFd, ioBuf);
        } catch (Exception e) {
            Logx.e(TAG, "唤醒通道 signal 失败", e);
        }
    }

    /** 读端角色：消费唤醒（fd 监听回调内调用；每次读 8B，与写对齐） */
    public void drain() {
        try {
            ioBuf.clear();
            Os.read(localFd, ioBuf);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EAGAIN) {
                Logx.e(TAG, "唤醒通道 drain 失败 errno=" + e.errno);
            }
        } catch (Exception e) {
            Logx.e(TAG, "唤醒通道 drain 异常", e);
        }
    }

    @Override
    public void close() {
        // 仅在 nativeDestroy 之后调用（C++ 持有的对端已不再使用）
        try {
            if (localFd != null) {
                Os.close(localFd);
            }
        } catch (ErrnoException e) {
            Logx.w(TAG, "本端 close 失败 errno=" + e.errno);
        }
        try {
            if (peerFd != null) {
                Os.close(peerFd);
            }
        } catch (ErrnoException e) {
            Logx.w(TAG, "对端 close 失败 errno=" + e.errno);
        }
        localFd = null;
        peerFd = null;
    }

    /** 调试用：协议字节数 */
    public static int protocolBytes() {
        return 8;
    }
}
