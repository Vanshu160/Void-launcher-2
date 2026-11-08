# Third-party notices

Void Launcher is licensed under the GNU General Public License v3.0 (see `LICENSE`).

## Planned: PojavLauncher engine components
- **Project:** PojavLauncher (fork used as reference: https://github.com/Vanshu160/PojavLauncher)
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), confirmed from the `LICENSE` file of that fork
- **Status:** No PojavLauncher code has been copied into Void Launcher yet.
- **Rule:** Any copied or adapted PojavLauncher file keeps its original LGPL-3.0 copyright notice, and the change is recorded here with the file name.

## Libraries (via Gradle)
These are downloaded at build time and are not copied into this repository.

| Library | Use | License |
|---|---|---|
| Kotlin and kotlinx | Language and coroutines | Apache-2.0 |
| AndroidX / Jetpack Compose (Material 3, icons, navigation, lifecycle, activity) | UI and architecture | Apache-2.0 |

Check each library's own license before adding it.

## Java runtime (downloaded on first use)
- **Source:** https://mojolauncher.github.io/jre-download/, the runtime host used by PojavLauncher.
- **Status: license of these runtimes is NOT yet verified.** Confirm it before any public release.
- **Signature certificate:** `app/src/main/assets/runtime-cert.pem`, copied from the MojoLauncher PojavLauncher fork (LGPL-3.0), file `app_pojavlauncher/src/main/assets/cert.pem`. It is used only to check runtime signatures.

## Libraries added in 0.6.0
| Library | Use | License |
|---|---|---|
| Apache Commons Compress 1.27.1 | Reads tar archives | Apache-2.0 |
| XZ for Java 1.10 | Decompresses .xz archives | Public domain (per project) |

## mojoexec (native, built at compile time)
- **Project:** mojoexec, by MojoLauncher (https://github.com/MojoLauncher/mojoexec)
- **License:** MIT, Copyright (c) 2026 Maksim Belov (full text in the upstream repository's LICENSE file)
- **How it's used:** CMake fetches the pinned commit `9ec4b836e93900135bad823eaac16ebc81cf6b98` during the build. No mojoexec source is copied into this repository.

## PojavLauncher fork (native hooks, built at compile time)
- **Project:** PojavLauncher fork (https://github.com/Vanshu160/PojavLauncher), based on MojoLauncher's PojavLauncher
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), from the fork's LICENSE file
- **Pinned commit:** `b32a74357b1f064193beebfe1baa12c226568353`
- **How it's used:** CMake fetches the pinned commit at build time and compiles only the JNI hook sources (`jvm_hooks/`, `jre_launcher/`, `utils.c`, `stdio_is.c`, `minibridge.c`). Nothing is copied into this repository.
- **Note:** the LGPL notices in those source files stay in the fetched copy. Void Launcher is GPL-3.0, which is compatible with LGPL-3.0.

## PojavLauncher fork (native hooks, built at compile time)
- **Project:** PojavLauncher fork (https://github.com/Vanshu160/PojavLauncher), based on MojoLauncher's PojavLauncher
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), from the fork's LICENSE file
- **Pinned commit:** `b32a74357b1f064193beebfe1baa12c226568353`
- **How it's used:** CMake fetches the pinned commit at build time and compiles only the JNI hook sources (`jvm_hooks/`, `jre_launcher/`, `utils.c`, `stdio_is.c`, `minibridge.c`). Nothing is copied into this repository.
- **Note:** the LGPL notices in those source files stay in the fetched copy. Void Launcher is GPL-3.0, which is compatible with LGPL-3.0.

## PojavLauncher fork (native hooks, built at compile time)
- **Project:** PojavLauncher fork (https://github.com/Vanshu160/PojavLauncher), based on MojoLauncher's PojavLauncher
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), from the fork's LICENSE file
- **Pinned commit:** `b32a74357b1f064193beebfe1baa12c226568353`
- **How it's used:** CMake fetches the pinned commit at build time and compiles only the JNI hook sources (`jvm_hooks/`, `jre_launcher/`, `utils.c`, `stdio_is.c`, `minibridge.c`). Nothing is copied into this repository.
- **Note:** the LGPL notices in those source files stay in the fetched copy. Void Launcher is GPL-3.0, which is compatible with LGPL-3.0.

## JNI bridge classes (written for Void Launcher)
- `net.kdt.pojavlaunch.utils.jre.JavaRunner` and `net.kdt.pojavlaunch.utils.JREUtils` are small Kotlin classes written for Void Launcher. Their package and method names must match the native code in libmojort.so (PojavLauncher, LGPL-3.0). No PojavLauncher Java code is copied.
- `GameLauncher` follows the steps of PojavLauncher's `JavaRunner.startJvm` (LGPL-3.0) in simplified form, rewritten in Kotlin.

## GLFW (MojoLauncher fork, Android build, native, built at compile time)
- **Project:** MojoLauncher's GLFW fork (https://github.com/MojoLauncher/glfw), based on GLFW (https://www.glfw.org)
- **License:** zlib/libpng license (full text in the upstream repository's LICENSE.md)
- **Pinned commit:** `7900e19fd2dbcad5abb4affc3b9d56c27e164c59`
- **How it's used:** CMake fetches the pinned commit at build time. No GLFW source is copied into this repository.

## MojoSDL (SDL3 fork for Android, native, built at compile time)
- **Project:** MojoSDL (https://github.com/MojoLauncher/MojoSDL), a fork of SDL3 (https://github.com/libsdl-org/SDL)
- **License:** zlib license (full text in the upstream repository's LICENSE.txt)
- **Pinned commit:** `b81efe99684acc5ec7e0ed94d941ac8e1c89bd42`
- **How it's used:** CMake fetches the pinned commit at build time. No SDL source is copied into this repository.

## mojoexec pin update
- The mojoexec pin used by the native build is now `27790c525c3f572d9611bfa05c532014be367ca0` (the commit the PojavLauncher fork uses). It replaces `9ec4b836e93900135bad823eaac16ebc81cf6b98`. The only difference is one line in `affinity.c`.

## libjnidispatch.so (JNA native dispatch library, Android build)
- **Source:** PojavLauncher fork, pinned commit `b32a74357b1f064193beebfe1baa12c226568353`, path `app_pojavlauncher/src/main/jniLibs/arm64-v8a/libjnidispatch.so`
- **Upstream project:** JNA (https://github.com/java-native-access/jna), dual licensed Apache-2.0 / LGPL-2.1
- **Why:** the JNA jar on the game's classpath tries to extract its own desktop-Linux build of this library, which needs glibc's `libc.so.6` and fails on Android's bionic libc. This is a build of the same native code against the Android NDK, matching JNA's JNI entry points, used by PojavLauncher in production.

## LWJGL 3.3.3 native libraries (arm64 Android), built in this repo's own CI
- **Source:** PojavLauncherTeam/lwjgl3 (https://github.com/PojavLauncherTeam/lwjgl3), branch `wip/rebase_3.3.3`
- **License:** BSD 3-Clause (same as upstream LWJGL)
- **How it's built:** `.github/workflows/build-lwjgl-arm64.yml` runs that project's own `ci_build_android.bash` for arm64 and publishes the resulting `.so` files as a GitHub Release (`lwjgl-3.3.3-arm64`) in this repository. The app downloads them at first launch (`Lwjgl333Natives.kt`), the same per-version natives folder pattern PojavLauncher's own `GameRunner` uses.
- **Status:** this is a real native compile of third-party, in-progress (unreleased) upstream code. It needs to be run and verified in GitHub Actions before it produces anything; see CHECKLIST.md.
