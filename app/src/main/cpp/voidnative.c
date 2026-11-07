#include <jni.h>

// Placeholder native entry point. Later milestones replace this with the engine bridge.
JNIEXPORT jstring JNICALL
Java_com_voidlauncher_app_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "voidnative 0.7.0a");
}
