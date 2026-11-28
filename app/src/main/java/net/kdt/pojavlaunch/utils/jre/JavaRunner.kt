package net.kdt.pojavlaunch.utils.jre

import android.content.Context

/**
 * JNI bridge to libmojort.so (PojavLauncher native hooks, LGPL-3.0).
 * Do not rename this class or its methods: the native code finds them by name.
 */
object JavaRunner {
    /**
     * Records the JVM and the exit handler used when the game aborts or exits. Must run before [nativeLoadJVM].
     * Without it, the native exit thread dereferences a NULL JavaVM and the process crashes (see the crash
     * report that showed SIGSEGV in libmojort.so inside the abort_wait thread).
     */
    @JvmStatic
    external fun nativeSetupExit(context: Context)

    @JvmStatic
    external fun nativeTransferArguments(args: Array<String>): Long

    @JvmStatic
    external fun nativeLoadJVM(
        vmPath: String,
        javaArgsL: Long,
        mainClass: String,
        appArgsL: Long,
        hasJavaAgents: Boolean,
    ): Boolean
}
