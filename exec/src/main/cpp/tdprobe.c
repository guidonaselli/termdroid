
#include <stdio.h>

int main(int argc, char **argv) {
    printf("TDPROBE_OK %s\n", argc > 1 ? argv[1] : "-");
    return 0;
}
