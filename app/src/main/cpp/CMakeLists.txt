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
