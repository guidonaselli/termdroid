// Primitivas de ejecucion para el spike de F-001.
//
// Una sola funcion generica: fork + execv con captura de stdout/stderr.
// Los tres niveles del modelo de ejecucion se distinguen unicamente por el
// argv que arma el lado Kotlin, no por caminos de codigo distintos:
//
//   nivel 0  argv = [ "<filesDir>/tdhello", ... ]
//   nivel 1  argv = [ "<nativeLibraryDir>/libtdhello.so", ... ]
//   nivel 2  argv = [ "/system/bin/linker64", "<filesDir>/tdhello", ... ]
//
// Que el fallo sea legible importa tanto como que el exito lo sea: si el
// nivel 2 no anda hay que poder decir por que. Ver 10_TECH/EXEC_MODEL.md.

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#define OUT_MAX 65536

static void append(char *buf, size_t *len, const char *fmt, ...) {
    if (*len >= OUT_MAX - 1) return;
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf + *len, OUT_MAX - *len, fmt, ap);
    va_end(ap);
    if (n > 0) *len += (size_t) n;
    if (*len > OUT_MAX - 1) *len = OUT_MAX - 1;
}

JNIEXPORT jstring JNICALL
Java_com_termdroid_spike_NativeExec_run(JNIEnv *env, jclass clazz,
                                        jobjectArray jargv, jstring jcwd) {
    (void) clazz;

    jsize argc = (*env)->GetArrayLength(env, jargv);
    if (argc <= 0) {
        return (*env)->NewStringUTF(env, "ERROR: argv vacio");
    }

    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jargv, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    argv[argc] = NULL;

    char *cwd = NULL;
    if (jcwd != NULL) {
        const char *c = (*env)->GetStringUTFChars(env, jcwd, NULL);
        cwd = strdup(c);
        (*env)->ReleaseStringUTFChars(env, jcwd, c);
    }

    char *out = malloc(OUT_MAX);
    size_t len = 0;
    out[0] = '\0';

    int pipefd[2];
    if (pipe(pipefd) != 0) {
        append(out, &len, "ERROR: pipe() errno=%d (%s)\n", errno, strerror(errno));
        goto done;
    }

    pid_t pid = fork();
    if (pid < 0) {
        append(out, &len, "ERROR: fork() errno=%d (%s)\n", errno, strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        goto done;
    }

    if (pid == 0) {
        // hijo
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);
        if (cwd != NULL) {
            if (chdir(cwd) != 0) {
                fprintf(stderr, "chdir fallo errno=%d\n", errno);
            }
        }
        execv(argv[0], argv);
        // Solo se llega aca si execv fallo. El errno es el dato que buscamos.
        fprintf(stderr, "EXECV_FAILED errno=%d (%s)\n", errno, strerror(errno));
        fflush(stderr);
        _exit(127);
    }

    // padre
    close(pipefd[1]);
    char chunk[4096];
    ssize_t n;
    while ((n = read(pipefd[0], chunk, sizeof(chunk))) > 0) {
        size_t room = OUT_MAX - 1 - len;
        size_t take = (size_t) n < room ? (size_t) n : room;
        memcpy(out + len, chunk, take);
        len += take;
        out[len] = '\0';
        if (room == 0) break;
    }
    close(pipefd[0]);

    int status = 0;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
        append(out, &len, "\n[exit=%d]\n", WEXITSTATUS(status));
    } else if (WIFSIGNALED(status)) {
        append(out, &len, "\n[signal=%d]\n", WTERMSIG(status));
    }

done:
    for (jsize i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    free(cwd);
    jstring res = (*env)->NewStringUTF(env, out);
    free(out);
    return res;
}
