// Binario de prueba del spike de ejecucion (F-001).
//
// Imprime lo suficiente para verificar los criterios de aceptacion:
//   - que ejecuto (marcador),
//   - como llego argv[0] (clave para el nivel 2: el linker lo desplaza),
//   - el resto de los argumentos,
//   - uid/pid, para confirmar bajo que identidad corre.

#include <stdio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    printf("TDHELLO_OK\n");
    printf("argc=%d\n", argc);
    for (int i = 0; i < argc; i++) {
        printf("argv[%d]=%s\n", i, argv[i]);
    }
    printf("uid=%d gid=%d pid=%d\n", getuid(), getgid(), getpid());
    fflush(stdout);
    return 0;
}
