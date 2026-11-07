cmake_minimum_required(VERSION 3.22.1)
project("voidnative")

add_library(voidnative SHARED voidnative.c)

find_library(log-lib log)
target_link_libraries(voidnative ${log-lib})
