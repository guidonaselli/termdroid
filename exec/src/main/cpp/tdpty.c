// PTY para el spike de F-001 (S3).
//
// Un shell util necesita una terminal de verdad, no un pipe: sin tty no hay
// control de trabajos, ni edicion de linea, ni programas de pantalla completa
// (vim, htop). forkpty() de bionic da eso.
//
// Ver 10_TECH/EXEC_MODEL.md, seccion PTY.

#include <jni.h>
#include <errno.h>
#include <pty.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

// Devuelve el fd del master en el int[0] y el pid del hijo en el int[1].
JNIEXPORT jintArray JNICALL
Java_com_termdroid_exec_NativePty_open(JNIEnv *env, jclass clazz,
                                        jobjectArray jargv, jobjectArray jenv,
                                        jstring jcwd, jint rows, jint cols) {
    (void) clazz;

    jsize argc = (*env)->GetArrayLength(env, jargv);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jargv, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    argv[argc] = NULL;

    jsize envc = jenv == NULL ? 0 : (*env)->GetArrayLength(env, jenv);
    char **envp = calloc((size_t) envc + 1, sizeof(char *));
    for (jsize i = 0; i < envc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jenv, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        envp[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    envp[envc] = NULL;

    char *cwd = NULL;
    if (jcwd != NULL) {
        const char *c = (*env)->GetStringUTFChars(env, jcwd, NULL);
        cwd = strdup(c);
        (*env)->ReleaseStringUTFChars(env, jcwd, c);
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;

    int master = -1;
    pid_t pid = forkpty(&master, NULL, NULL, &ws);

    if (pid < 0) {
        master = -errno;
    } else if (pid == 0) {
        // hijo: ya tiene el slave como stdin/stdout/stderr y es lider de sesion
        if (cwd != NULL) {
            if (chdir(cwd) != 0) { /* el shell arranca igual desde donde este */ }
        }
        // Sin esto, un Ctrl-C en la app mataria al hijo junto con el padre.
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        execve(argv[0], argv, envp);
        _exit(127);
    }

    jintArray res = (*env)->NewIntArray(env, 2);
    jint vals[2] = {master, (jint) pid};
    (*env)->SetIntArrayRegion(env, res, 0, 2, vals);

    for (jsize i = 0; i < argc; i++) free(argv[i]);
    for (jsize i = 0; i < envc; i++) free(envp[i]);
    free(argv);
    free(envp);
    free(cwd);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_termdroid_exec_NativePty_read(JNIEnv *env, jclass clazz, jint fd, jbyteArray buf) {
    (void) clazz;
    jsize len = (*env)->GetArrayLength(env, buf);
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    ssize_t n = read(fd, b, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, 0);
    // EIO al cerrarse el slave es fin de stream, no un error que reportar.
    if (n < 0 && errno == EIO) return -1;
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_com_termdroid_exec_NativePty_write(JNIEnv *env, jclass clazz, jint fd, jbyteArray buf) {
    (void) clazz;
    jsize len = (*env)->GetArrayLength(env, buf);
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    ssize_t n = write(fd, b, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, JNI_ABORT);
    return (jint) n;
}

JNIEXPORT void JNICALL
Java_com_termdroid_exec_NativePty_resize(JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void) env;
    (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_termdroid_exec_NativePty_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_termdroid_exec_NativePty_close(JNIEnv *env, jclass clazz, jint fd, jint pid) {
    (void) env;
    (void) clazz;
    if (fd >= 0) close(fd);
    if (pid > 0) kill((pid_t) pid, SIGHUP);
}
