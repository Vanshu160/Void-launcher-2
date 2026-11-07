cmake_minimum_required(VERSION 3.22.1)
project("voidnative")

# mojoexec (MIT, Copyright (c) 2026 Maksim Belov, MojoLauncher).
# Pinned to one commit so a later upstream change cannot break this build unnoticed.
include(FetchContent)
FetchContent_Declare(
    mojoexec
    GIT_REPOSITORY https://github.com/MojoLauncher/mojoexec.git
    GIT_TAG 9ec4b836e93900135bad823eaac16ebc81cf6b98
)
FetchContent_MakeAvailable(mojoexec)

add_library(voidnative SHARED voidnative.c)

find_library(log-lib log)
target_link_libraries(voidnative mojoexec ${log-lib})
