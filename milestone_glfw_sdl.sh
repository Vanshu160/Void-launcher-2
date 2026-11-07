#!/usr/bin/env bash
# Void Launcher 0.7.0d - build MojoLauncher's Android GLFW and MojoSDL (SDL3 fork) for arm64.
# Settings and pinned commits are taken from PojavLauncher's own native build:
#   app_pojavlauncher/src/main/jni/CMakeLists.txt, and the submodule pins in its git tree.
# Nothing is linked into voidnative yet. This step only proves the libraries build in CI.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

mkdir -p app/src/main/cpp

# ---------------------------------------------------------------------------
cat > app/src/main/cpp/CMakeLists.txt << 'CMAKE_EOF'
cmake_minimum_required(VERSION 3.22.1)
project("voidnative")

include(FetchContent)

# mojoexec (MIT, Copyright (c) 2026 Maksim Belov, MojoLauncher), pinned to the commit
# the PojavLauncher fork uses. It differs from the earlier pin by one line in affinity.c.
FetchContent_Declare(
    mojoexec
    GIT_REPOSITORY https://github.com/MojoLauncher/mojoexec.git
    GIT_TAG 27790c525c3f572d9611bfa05c532014be367ca0
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

# ------ GLFW (zlib/libpng), MojoLauncher fork with Android support, pinned ------
# Same options as the PojavLauncher fork.
set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)

FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/MojoLauncher/glfw.git
    GIT_TAG 7900e19fd2dbcad5abb4affc3b9d56c27e164c59
    GIT_SUBMODULES ""
)
FetchContent_MakeAvailable(glfw)
target_compile_options(glfw PRIVATE -Wno-nullability-completeness)

# ------ MojoSDL (SDL3 fork, zlib), pinned ------
# Same options as the PojavLauncher fork.
set(SDL_SHARED       ON  CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON  CACHE BOOL "" FORCE)
set(SDL_AUDIO        ON  CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_CAMERA       OFF CACHE BOOL "" FORCE)
set(SDL_TESTS        OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES     OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    sdl
    GIT_REPOSITORY https://github.com/MojoLauncher/MojoSDL.git
    GIT_TAG b81efe99684acc5ec7e0ed94d941ac8e1c89bd42
    GIT_SUBMODULES ""
)
FetchContent_MakeAvailable(sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)

# ------ Launcher hooks (JVM side), from the PojavLauncher fork ------
find_library(log-lib log)

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
target_link_libraries(mojort PRIVATE mojoexec ${log-lib})

add_library(voidnative SHARED voidnative.c)
target_link_libraries(voidnative mojort mojoexec ${log-lib})
CMAKE_EOF

# ---------------------------------------------------------------------------
cat >> THIRD_PARTY.md << 'MD_EOF'

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
MD_EOF

# ---------------------------------------------------------------------------
cat >> CHECKLIST.md << 'MD_EOF'

## Milestone 0.7.0d - GLFW and MojoSDL build (in progress)
- [x] Pinned MojoLauncher GLFW (`7900e19`) and MojoSDL (`b81efe9`), with the fork's build options
- [x] mojoexec pin matched to the fork (`27790c5`)
- [ ] CI build passes (arm64 `libglfw.so` and `libSDL3.so` built, not linked yet)
- [ ] Java bridge classes (`git.artdeell.dnbootstrap.glfw`, `git.mojo.sdl`) added, next step
MD_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Files written for milestone 0.7.0d (GLFW and MojoSDL build)."
echo "Next:  git add . && git commit -m \"Milestone 0.7.0d: build MojoLauncher GLFW and MojoSDL\" && git push"
