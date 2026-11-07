#!/usr/bin/env bash
# Void Launcher 0.7.0c - build the launcher hook library (mojort) from a pinned PojavLauncher fork commit.
# Only the hook sources are compiled. Nothing is copied into this repo, and nothing calls the library yet.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

# ---------------------------------------------------------------------------
cat > app/src/main/cpp/CMakeLists.txt << 'CMAKE_EOF'
cmake_minimum_required(VERSION 3.22.1)
project("voidnative")

include(FetchContent)

# mojoexec (MIT, Copyright (c) 2026 Maksim Belov, MojoLauncher), pinned.
FetchContent_Declare(
    mojoexec
    GIT_REPOSITORY https://github.com/MojoLauncher/mojoexec.git
    GIT_TAG 9ec4b836e93900135bad823eaac16ebc81cf6b98
)
FetchContent_MakeAvailable(mojoexec)

# PojavLauncher fork (LGPL-3.0), pinned. Only its JNI hook sources are used.
# Submodules are skipped on purpose: the engine parts we do not need are not fetched.
FetchContent_Declare(
    pojavlauncher
    GIT_REPOSITORY https://github.com/Vanshu160/PojavLauncher.git
    GIT_TAG b32a74357b1f064193beebfe1baa12c226568353
    GIT_SUBMODULES ""
)
FetchContent_Populate(pojavlauncher)

set(POJAV_JNI "${pojavlauncher_SOURCE_DIR}/app_pojavlauncher/src/main/jni")

add_library(mojort SHARED
        ${POJAV_JNI}/jvm_hooks/emui_iterator_fix_hook.c
        ${POJAV_JNI}/jvm_hooks/java_exec_hooks.c
        ${POJAV_JNI}/jvm_hooks/lwjgl_dlopen_hook.c
        ${POJAV_JNI}/jre_launcher/jre_launcher.c
        ${POJAV_JNI}/jre_launcher/native_library_hook.c
        ${POJAV_JNI}/jre_launcher/elf_hinter.c
        ${POJAV_JNI}/jre_launcher/abort_wait.c
        ${POJAV_JNI}/utils.c
        ${POJAV_JNI}/stdio_is.c
        ${POJAV_JNI}/minibridge.c
)
target_include_directories(mojort PRIVATE ${POJAV_JNI})

find_library(log-lib log)
target_link_libraries(mojort PRIVATE mojoexec ${log-lib})

add_library(voidnative SHARED voidnative.c)
target_link_libraries(voidnative mojort mojoexec ${log-lib})
CMAKE_EOF

# ---------------------------------------------------------------------------
cat >> THIRD_PARTY.md << 'MD_EOF'

## PojavLauncher fork (native hooks, built at compile time)
- **Project:** PojavLauncher fork (https://github.com/Vanshu160/PojavLauncher), based on MojoLauncher's PojavLauncher
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), from the fork's LICENSE file
- **Pinned commit:** `b32a74357b1f064193beebfe1baa12c226568353`
- **How it's used:** CMake fetches the pinned commit at build time and compiles only the JNI hook sources (`jvm_hooks/`, `jre_launcher/`, `utils.c`, `stdio_is.c`, `minibridge.c`). Nothing is copied into this repository.
- **Note:** the LGPL notices in those source files stay in the fetched copy. Void Launcher is GPL-3.0, which is compatible with LGPL-3.0.
MD_EOF

# ---------------------------------------------------------------------------
cat > CHECKLIST.md << 'MD_EOF'
# Void Launcher development checklist

## Milestone 0.1.0 - Foundation (complete)
- [x] Gradle skeleton, CI APK build, theme, navigation, Home screen
- [x] License: GPL-3.0 (`LICENSE`) and third-party notices (`THIRD_PARTY.md`)

## Milestone 0.2.0 - Version manager (complete)
- [x] Mojang version manifest, offline cache, release and snapshot filters

## Milestone 0.3.0 - Landscape UI overhaul (complete)
- [x] Landscape layout, animated backdrop, top tabs, Settings tabs

## Milestone 0.4.0 - Mods (Fabric via Modrinth)
- [x] Search Modrinth for Fabric mods matching the selected version
- [x] Install with SHA-1 check and required dependencies (one level)
- [x] Per-version mods folder, installed list, remove
- [x] Selected version shared between Versions, Home and Mods
- [x] CI build passes
- [ ] Verified on a phone
- [ ] Fabric loader install (needs the engine milestone)

## Milestone 0.5.0 - Game files download (complete)
- [x] Version metadata, client jar, libraries and assets downloaded with SHA-1 checks
- [x] Existing valid files are skipped, so an interrupted download can resume
- [x] Downloads screen with progress, cancel and error messages
- [x] CI build passes
- [x] Verified on a phone (Minecraft 1.21.11 downloaded)

## Milestone 0.6.0 - Java runtime (complete)
- [x] Runtime chosen from the version's required Java major (8, 17, 21, 25)
- [x] Archives checked with RSA-4096 signatures before unpacking
- [x] Java 21 downloaded and ready on device
- [ ] Runtime license verified (blocks public release)
- [ ] unpack200 step and libawt dummy (needed before the engine can run)

## Milestone 0.7.0 - Native engine (in progress)
- [x] 0.7.0a: NDK 29 and CMake 3.22.1 build a native library in CI
- [x] 0.7.0b: mojoexec built from a pinned commit in CI
- [ ] 0.7.0c: launcher hooks (mojort) built from the pinned PojavLauncher fork (CI check pending)
- [ ] 0.7.0d: GLFW
- [ ] 0.7.0e: MojoSDL

## Later milestones
- [ ] Java bridge classes that the native hooks call (net.kdt.pojavlaunch.*)
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Pojav adapter (first game launch)
- [ ] Touch controls
- [ ] Logs and diagnostics
MD_EOF

echo
echo "Done. Files written for milestone 0.7.0c."
echo "Next:  git add . && git commit -m \"Milestone 0.7.0c: build launcher hooks\" && git push"
