/*
 * Minimal PTY spawner for Android.
 *
 * Android's bionic libc has no forkpty(3), so we open /dev/ptmx ourselves,
 * fork, attach the child to the slave side as its controlling terminal and
 * exec the requested program. The master fd is returned to Kotlin, which
 * pumps bytes to xterm.js.
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <android/log.h>

#define LOG_TAG "openCarlPty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static char **to_c_array(JNIEnv *env, jobjectArray array, int extra) {
    if (array == NULL) {
        char **out = calloc((size_t) extra + 1, sizeof(char *));
        return out;
    }
    jsize len = (*env)->GetArrayLength(env, array);
    char **out = calloc((size_t) len + (size_t) extra + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < len; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        if (item == NULL) continue;
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        out[i] = utf ? strdup(utf) : NULL;
        if (utf) (*env)->ReleaseStringUTFChars(env, item, utf);
        (*env)->DeleteLocalRef(env, item);
    }
    return out;
}

static void free_c_array(char **array) {
    if (array == NULL) return;
    for (char **p = array; *p != NULL; p++) free(*p);
    free(array);
}

JNIEXPORT jintArray JNICALL
Java_dev_opencarl_terminal_NativePty_create(JNIEnv *env, jclass clazz,
                                              jstring cmd, jobjectArray args,
                                              jobjectArray envp, jstring cwd,
                                              jint rows, jint cols) {
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) return NULL;
    int values[2] = {-1, -1};

    if (cmd == NULL) {
        (*env)->SetIntArrayRegion(env, result, 0, 2, values);
        return result;
    }

    const char *cmd_utf = (*env)->GetStringUTFChars(env, cmd, NULL);
    if (cmd_utf == NULL) {
        (*env)->SetIntArrayRegion(env, result, 0, 2, values);
        return result;
    }

    char **argv = to_c_array(env, args, 1);
    char **envv = to_c_array(env, envp, 0);
    if (argv == NULL || envv == NULL) {
        free_c_array(argv);
        free_c_array(envv);
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
        (*env)->SetIntArrayRegion(env, result, 0, 2, values);
        return result;
    }
    argv[0] = strdup(cmd_utf);

    char *cwd_utf = NULL;
    if (cwd != NULL) {
        const char *tmp = (*env)->GetStringUTFChars(env, cwd, NULL);
        if (tmp != NULL) {
            cwd_utf = strdup(tmp);
            (*env)->ReleaseStringUTFChars(env, cwd, tmp);
        }
    }

    int ptm = open("/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (ptm < 0) {
        LOGE("open(/dev/ptmx) failed: %s", strerror(errno));
        goto done;
    }
    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(ptm);
        goto done;
    }
    char *pts_name = ptsname(ptm);
    if (pts_name == NULL) {
        LOGE("ptsname failed: %s", strerror(errno));
        close(ptm);
        goto done;
    }
    char slave_path[256];
    strncpy(slave_path, pts_name, sizeof(slave_path) - 1);
    slave_path[sizeof(slave_path) - 1] = '\0';

    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    size.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ioctl(ptm, TIOCSWINSZ, &size);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(ptm);
        goto done;
    }

    if (pid == 0) {
        /* Child: only async-signal-safe calls from here on. */
        setsid();
        int slave = open(slave_path, O_RDWR);
        if (slave < 0) _exit(126);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(ptm);

        sigset_t empty;
        sigemptyset(&empty);
        sigprocmask(SIG_SETMASK, &empty, NULL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        signal(SIGTERM, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);

        if (cwd_utf != NULL) chdir(cwd_utf);
        execve(cmd_utf, argv, envv);
        _exit(127);
    }

    values[0] = ptm;
    values[1] = (int) pid;

done:
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    free_c_array(argv);
    free_c_array(envv);
    free(cwd_utf);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
    return result;
}

JNIEXPORT jint JNICALL
Java_dev_opencarl_terminal_NativePty_read(JNIEnv *env, jclass clazz, jint fd,
                                            jbyteArray buffer) {
    if (buffer == NULL) return -1;
    jsize len = (*env)->GetArrayLength(env, buffer);
    if (len <= 0) return 0;
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) return -1;
    ssize_t n;
    do {
        n = read(fd, bytes, (size_t) len);
    } while (n < 0 && errno == EINTR);
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, 0);
    if (n < 0) return (errno == EIO || errno == EAGAIN) ? 0 : -1;
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_dev_opencarl_terminal_NativePty_write(JNIEnv *env, jclass clazz, jint fd,
                                             jbyteArray buffer, jint offset, jint length) {
    if (buffer == NULL) return -1;
    jsize len = (*env)->GetArrayLength(env, buffer);
    if (offset < 0 || length < 0 || offset + length > len) return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) return -1;
    ssize_t written = 0;
    while (written < length) {
        ssize_t n = write(fd, bytes + offset + written, (size_t) (length - written));
        if (n < 0) {
            if (errno == EINTR) continue;
            written = -1;
            break;
        }
        written += n;
    }
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
    return (jint) written;
}

JNIEXPORT jint JNICALL
Java_dev_opencarl_terminal_NativePty_setSize(JNIEnv *env, jclass clazz, jint fd,
                                               jint rows, jint cols) {
    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = (unsigned short) rows;
    size.ws_col = (unsigned short) cols;
    return ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL
Java_dev_opencarl_terminal_NativePty_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    int status = 0;
    pid_t r;
    do {
        r = waitpid((pid_t) pid, &status, 0);
    } while (r < 0 && errno == EINTR);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_dev_opencarl_terminal_NativePty_close(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) close(fd);
}
