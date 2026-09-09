# Tikeno — ProGuard/R8 规则（架构 §2.2 #10 / §5.1）
#
# 说明：JNI 采用 RegisterNatives 静态注册，与 Java 方法名解耦，
# 理论上无需保留 native 方法名；此处仍保留作为双保险（架构 §5.1）。

# —— JNI 边界 ——
# 保留 native 方法名（RegisterNatives 的兜底）
-keepclasseswithmembernames class * {
    native <methods>;
}
# NativeEngine：native 声明所在类，禁止改名/裁剪
-keep class com.tikeno.autoclicker.jni.NativeEngine { *; }
# FileDescriptor.descriptor：JNI 反射读取 fd 的唯一允许点（架构 §10.2.4）
-keepclassmembers class java.io.FileDescriptor {
    private int descriptor;
    private long handle;
}

# —— Shizuku（L2 档，T03 起使用） ——
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class android.content.IContentProvider { *; }

# —— AppCompat / Material ——
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.internal.** { *; }
-dontwarn androidx.appcompat.**
-dontwarn com.google.android.material.**

# —— 通用 ——
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn org.slf4j.**
