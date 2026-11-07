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
