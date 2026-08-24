cmake_minimum_required(VERSION 3.22.1)
project(termdroid_spike C)

set(CMAKE_C_STANDARD 11)

# --- JNI helper ---------------------------------------------------------
# Libreria compartida normal. Expone las primitivas de ejecucion al lado Kotlin.
add_library(tdexec SHARED tdexec.c)
target_link_libraries(tdexec log)

# --- Binario de prueba --------------------------------------------------
# Es un EJECUTABLE, no una libreria, pero se nombra lib*.so para que el
# packager del APK lo extraiga a nativeLibraryDir. Ese naming es la
# restriccion real del nivel 1. Ver 10_TECH/EXEC_MODEL.md.
add_executable(tdhello tdhello.c)
set_target_properties(tdhello PROPERTIES
    OUTPUT_NAME "tdhello"
    PREFIX "lib"
    SUFFIX ".so")
# PIE dinamico: condicion del nivel 2 (el linker de Android no carga estaticos).
target_link_options(tdhello PRIVATE -pie)
