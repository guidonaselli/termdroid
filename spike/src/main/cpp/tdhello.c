// Binario de prueba del spike de ejecucion (F-001).

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
