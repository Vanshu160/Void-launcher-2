package net.kdt.pojavlaunch.utils.jre

/**
 * JNI bridge to libmojort.so (PojavLauncher native hooks, LGPL-3.0).
 * Do not rename this class or its methods: the native code finds them by name.
 */
object JavaRunner {
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
