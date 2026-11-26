# GLFW fork patches

Void Launcher builds the MojoLauncher GLFW fork (`https://github.com/MojoLauncher/glfw.git`) at the pinned commit
`7900e19fd2dbcad5abb4affc3b9d56c27e164c59`. CMake fetches it at build time (`app/src/main/cpp/CMakeLists.txt`) and
applies every patch in this folder with `git apply`. No fork source is copied into this repository.

The fork is zlib/libpng licensed (GLFW). The patches are offered under the same license.

## Patches

| File | Purpose |
|---|---|
| `0001-void-input-queue-and-surface-lifecycle.patch` | Thread-safe input queue, queued mouse position, UTF-16 to code point decoding, non-blocking surface destroy, exactly-once `ANativeWindow` release, EGL surface cleanup on detach, lock around the GL-thread switch. |

## What 0001 changes

- `src/android_input_queue.{c,h}`: every push and dequeue takes `wait_mutex`. The consumer swaps buffers under the lock and runs callbacks without it. Wakeups cannot be lost. A mouse position event that directly follows another position event replaces it, so fast movement cannot fill the buffer and push out button or key events. Other events are dropped only as a last resort, and the drop is logged.
- `src/android_window.c`:
  - Mouse position is queued as an event instead of set through a shared flag.
  - `sendBulkUnicodeEvent` decodes surrogate pairs and frees its payload on every path.
  - `nativeSurfaceCreated` removes the extra `ANativeWindow_acquire`. `ANativeWindow_fromSurface` already returns a reference that the caller owns (NDK documentation). The reference is released exactly once.
  - `nativeSurfaceDestroyed` no longer waits on the render thread. It destroys the EGL window surface, which EGL keeps alive until the render thread releases it, and releases the `ANativeWindow` reference.
  - The render-thread surface switch and the swap check hold `nw_egl_mutex`. No GLFW callback runs while the lock is held. A failed `eglCreateWindowSurface` is logged.
  - `ensure_comm_connected` reads the JNI environment through a `void*`, as the `GetEnv` and `AttachCurrentThreadAsDaemon` APIs require. This removes a pointer-type warning from the original code.

## Regenerating the patch

```sh
git clone https://github.com/MojoLauncher/glfw.git glfw-src
git -C glfw-src checkout 7900e19fd2dbcad5abb4affc3b9d56c27e164c59
# edit files in glfw-src/src, then:
git -C glfw-src diff -- src/ > 0001-void-input-queue-and-surface-lifecycle.patch
```

Check that it applies to a clean checkout before committing it.

## Verification status

- `git apply --check` succeeds on a clean checkout of the pinned commit.
- `src/android_input_queue.c`: compiled on the host with stub headers. The host test covers 3 concurrent producers with 60,001 events (no loss, per-producer order preserved, wakeup works), and a position flood that must coalesce while keeping a button between positions in order. Run under ThreadSanitizer with no reports.
- `src/android_window.c`: `gcc -fsyntax-only` against the JDK JNI header and stub Android, EGL, and Vulkan headers. Zero warnings. This checks syntax and types only. It is not an NDK build.
- Every JNI export in `src/android_window.c` and `src/android_joystick.c` matches its Java `native` declaration in `GLFW.java`, by name and argument list.
- Not verified: the NDK build, runtime behaviour on a device, and the EGL behaviour described above.

## Swap surface read under lock (EGL race fix)

`swapBuffersEGL` (egl_context.c) read `window->context.egl.surface` without `nw_egl_mutex`, while `nativeSurfaceDestroyed` clears it on another thread. The new `_glfwSwapSurfaceAndroid` (android_window.c, declared in android_egl_context_hook.h) reads it under the lock. If no surface is present, the swap is skipped until the next surface callback restores one.
