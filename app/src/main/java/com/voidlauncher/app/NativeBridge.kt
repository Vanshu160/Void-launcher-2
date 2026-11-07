package com.voidlauncher.app

/**
 * Entry points of voidnative (app/src/main/cpp/voidnative.c).
 * The JNI names must match this package and class name.
 */
object NativeBridge {
    init {
        System.loadLibrary("voidnative")
    }

    @JvmStatic
    external fun nativeVersion(): String

    /** Writes signal, fault address, call stack and memory map to [path] if the process crashes natively. */
    @JvmStatic
    external fun installCrashLog(path: String)
}
