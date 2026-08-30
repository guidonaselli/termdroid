cmake_minimum_required(VERSION 3.22.1)
project(termdroid_exec C)

set(CMAKE_C_STANDARD 11)

# Primitivas de ejecucion y de PTY expuestas al lado Kotlin.
add_library(tdexec SHARED tdexec.c)
target_link_libraries(tdexec log)

add_library(tdpty SHARED tdpty.c)
target_link_libraries(tdpty log)

# Ejecutable del probe. Se nombra lib*.so para que el packager del APK lo
# extraiga a nativeLibraryDir, que es la unica ruta ejecutable garantizada.
add_executable(tdprobe tdprobe.c)
set_target_properties(tdprobe PROPERTIES
    OUTPUT_NAME "tdprobe"
    PREFIX "lib"
    SUFFIX ".so")
target_link_options(tdprobe PRIVATE -pie)

# CLI nativo para la terminal (claude, codex, gemini, agy)
add_executable(tdcli tdcli.c)
set_target_properties(tdcli PROPERTIES
    OUTPUT_NAME "tdcli"
    PREFIX "lib"
    SUFFIX ".so")
target_link_options(tdcli PRIVATE -pie)
target_link_libraries(tdcli log)
