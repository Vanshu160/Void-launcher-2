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
