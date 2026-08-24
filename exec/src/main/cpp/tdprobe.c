// Binario minimo que usa el capability probe.
//
// Su unico trabajo es existir, ejecutarse y decirlo. Se empaqueta como
// lib*.so para poder correr desde nativeLibraryDir (nivel 1) y se copia a
// filesDir para medir los niveles 0 y 2. Ver 10_TECH/EXEC_MODEL.md.

#include <stdio.h>

int main(int argc, char **argv) {
    printf("TDPROBE_OK %s\n", argc > 1 ? argv[1] : "-");
    return 0;
}
