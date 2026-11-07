#include <jni.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

// Native crash catcher. When the process gets a fatal signal, the signal, fault address,
// call stack and memory map are appended to the launch log, then the default crash runs.
// The memory map lets us match the addresses to library names later.

static char crash_log_path[512];

static void write_text(int fd, const char *text) {
    size_t len = strlen(text);
    (void) write(fd, text, len);
}

static void write_hex(int fd, uintptr_t value) {
    char digits[32];
    int count = 0;
    do {
        digits[count++] = "0123456789abcdef"[value & 0xf];
        value >>= 4;
    } while (value != 0 && count < 31);
    char out[40];
    int pos = 0;
    out[pos++] = '0';
    out[pos++] = 'x';
    while (count > 0) {
        out[pos++] = digits[--count];
    }
    out[pos] = '\0';
    write_text(fd, out);
}

struct backtrace_state {
    int fd;
    int frames;
};

static _Unwind_Reason_Code collect_frame(struct _Unwind_Context *context, void *arg) {
    struct backtrace_state *state = (struct backtrace_state *) arg;
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc == 0 || state->frames >= 80) {
        return _URC_END_OF_STACK;
    }
    write_text(state->fd, "  pc ");
    write_hex(state->fd, pc);
    write_text(state->fd, "\n");
    state->frames++;
    return _URC_NO_REASON;
}

static void copy_memory_map(int fd) {
    int in = open("/proc/self/maps", O_RDONLY);
    if (in < 0) {
        write_text(fd, "(memory map unavailable)\n");
        return;
    }
    char buffer[1024];
    ssize_t n;
    while ((n = read(in, buffer, sizeof buffer)) > 0) {
        (void) write(fd, buffer, (size_t) n);
    }
    close(in);
}

static void crash_handler(int signal_number, siginfo_t *info, void *context) {
    (void) context;
    int fd = open(crash_log_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        write_text(fd, "\n=== NATIVE CRASH ===\nsignal ");
        write_hex(fd, (uintptr_t) signal_number);
        write_text(fd, "  fault address ");
        write_hex(fd, info != NULL ? (uintptr_t) info->si_addr : 0);
        write_text(fd, "\ncall stack:\n");

        struct backtrace_state state = { fd, 0 };
        _Unwind_Backtrace(collect_frame, &state);

        write_text(fd, "--- memory map ---\n");
        copy_memory_map(fd);
        write_text(fd, "=== END NATIVE CRASH ===\n");
        close(fd);
    }
    // Let the system handle the crash normally afterwards.
    signal(signal_number, SIG_DFL);
    raise(signal_number);
}

JNIEXPORT jstring JNICALL
Java_com_voidlauncher_app_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "voidnative 0.8.2");
}

JNIEXPORT void JNICALL
Java_com_voidlauncher_app_NativeBridge_installCrashLog(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    const char *text = (*env)->GetStringUTFChars(env, path, NULL);
    if (text == NULL) {
        return;
    }
    snprintf(crash_log_path, sizeof crash_log_path, "%s", text);
    (*env)->ReleaseStringUTFChars(env, path, text);

    struct sigaction action;
    memset(&action, 0, sizeof action);
    action.sa_sigaction = crash_handler;
    action.sa_flags = SA_SIGINFO;
    sigemptyset(&action.sa_mask);

    sigaction(SIGSEGV, &action, NULL);
    sigaction(SIGABRT, &action, NULL);
    sigaction(SIGBUS, &action, NULL);
    sigaction(SIGILL, &action, NULL);
    sigaction(SIGFPE, &action, NULL);
}
