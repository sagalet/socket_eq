#include <jni.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <android/log.h>
#include <errno.h>
#include <poll.h>

#define TOTAL_EQ 7
#define EQ_PATH "/sys/class/i2c-adapter/i2c-1/1-0034"
#define MAX_VALUE 10

typedef struct jvm_context {
    JavaVM *vm;
    int enable;
    int eq[TOTAL_EQ];
}JvmContext;

JvmContext g_ctx;

// Android log function wrappers
static const char* kTAG = "js_equalizer";
#define DEBUG 1
#if DEBUG
#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, kTAG, __VA_ARGS__))
#else
#define LOGI(...)
#define LOGW(...)
#endif
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

void release() {
    if(g_ctx.enable!=-1) {
        close(g_ctx.enable);
        g_ctx.enable = -1;
    }

    for(int i=0; i<TOTAL_EQ; i++) {
        if(g_ctx.eq[i] != -1) {
            close(g_ctx.eq[i]);
            g_ctx.eq[i] = -1;
        }
    }
}

extern "C" {
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {

    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("get JNIenv error");
        return JNI_ERR;
    }

    memset(&g_ctx, 0, sizeof(g_ctx));
    g_ctx.vm = vm;
    g_ctx.enable = -1;
    for(int i=0; i<TOTAL_EQ; i++) {
        g_ctx.eq[i] = -1;
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT bool JNICALL
Java_com_example_eq_EqualizerImpl_nativeInit(
        JNIEnv *env, jobject obj){
    char path[50];
    memset(path, 0, 50);
    sprintf(path,EQ_PATH"/adau_eq");
    g_ctx.enable = open(path, O_RDWR);
    if(g_ctx.enable == -1) {
        LOGE("open adau_eq failed");
        return false;
    }

    for(int i=0; i<TOTAL_EQ; i++) {
        memset(path, 0, 50);
        sprintf(path, EQ_PATH"/eq%d", i + 1);
        g_ctx.eq[i] = open(path, O_RDWR);
        if (g_ctx.eq[i] == -1) {
            LOGE("open %s failed", path);
            goto error;
        }
    }
    return true;
error:
    release();
    return false;
};

JNIEXPORT void JNICALL
Java_com_example_eq_EqualizerImpl_nativeEnableEq(
        JNIEnv *env, jobject obj, bool enable) {
    int val = (enable?1:0);
    char buf[3];
    size_t size = sprintf(buf, "%d", val);
    write(g_ctx.enable, buf, size);
}

JNIEXPORT bool JNICALL
Java_com_example_eq_EqualizerImpl_nativeAdjustEq(
        JNIEnv *env, jobject obj, jint index, jint adjust) {
    if(index >= TOTAL_EQ) {
        LOGE("index is out of the range(%d)", index);
        return false;
    }

    if(adjust>=MAX_VALUE || adjust<=-MAX_VALUE) {
        LOGE("adjust value is out of the range(%d)", adjust);
        return false;
    }

    char buf[4];
    size_t size = sprintf(buf, "%d", adjust);

    if(write(g_ctx.eq[index], buf, size) == size) {
        return true;
    }
    return false;

}

JNIEXPORT jboolean JNICALL
Java_com_example_eq_EqualizerImpl_nativeGetEqState(
        JNIEnv *env, jobject obj) {
    if(g_ctx.enable!=-1) {
        char buf[4] = {0};
        lseek(g_ctx.enable, 0, SEEK_SET);
        size_t size = read(g_ctx.enable, buf, 4);
        int val = atoi(buf);
        LOGI("enabled? %s(%d)(%s)", buf, val, strerror(errno));
        return (val==1? true:false);
    }
    return false;
}

JNIEXPORT jint JNICALL
Java_com_example_eq_EqualizerImpl_nativeGetEqFrequency(
        JNIEnv *env, jobject obj, int index) {
    if(index >= TOTAL_EQ) {
        LOGE("index is out of range(%d)", index);
        goto failed;
    }

    if(g_ctx.eq[index] != -1) {
        char buf[10] = {0};
        lseek(g_ctx.eq[index], 0, SEEK_SET);
        size_t size = read(g_ctx.eq[index], buf, 10);

        //LOGI("getEqFrequency %s", buf);

        char *tmp = buf;
        char *tok = NULL;
        tok = strtok(tmp, ":");
        int val = atoi(tok);

        return val;

    }

failed:
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_example_eq_EqualizerImpl_nativeGetEqValue(
        JNIEnv *env, jobject obj, int index) {

    if(index >= TOTAL_EQ) {
        LOGE("index is out of range(%d)", index);
        goto failed;
    }

    if(g_ctx.eq[index] != -1) {
        char buf[10] = {0};
        lseek(g_ctx.eq[index], 0, SEEK_SET);
        size_t size = read(g_ctx.eq[index], buf, 10);

        //LOGI("getEqValue %s", buf);

        char *tmp = buf;
        char *tok = NULL;
        tok = strtok(tmp, ":");
        tok = strtok(NULL, ":");
        int val = atoi(tok);

        return val;
    }

failed:
    return -(MAX_VALUE+1);
}

JNIEXPORT void JNICALL
Java_com_example_eq_EqualizerImpl_nativeRelease(
        JNIEnv *env, jobject obj){
    release();
}
}
