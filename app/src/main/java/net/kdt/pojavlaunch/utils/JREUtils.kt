package net.kdt.pojavlaunch.utils

/**
 * JNI bridge to libmojort.so (PojavLauncher native hooks, LGPL-3.0).
 * Do not rename this class or its methods: the native code finds them by name.
 */
object JREUtils {
    @JvmStatic
    external fun setLdLibraryPath(ldLibraryPath: String)

    @JvmStatic
    external fun chdir(path: String): Int
}
