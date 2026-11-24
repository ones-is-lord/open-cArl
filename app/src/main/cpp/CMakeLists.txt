cmake_minimum_required(VERSION 3.22.1)
project(ptyexec C)

add_library(ptyexec SHARED pty.c)
target_compile_options(ptyexec PRIVATE -O2 -Wall -Wextra)
target_link_libraries(ptyexec log)
