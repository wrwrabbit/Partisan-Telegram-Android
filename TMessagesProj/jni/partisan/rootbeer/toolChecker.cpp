/****************************************************************************
 * Root checking JNI code, vendored from RootBeer 0.1.2
 * (https://github.com/scottyab/rootbeer), Apache-2.0. Original author:
 * Matthew Rollings, 2015.
 *
 * Compiled into the main tmessages library rather than a standalone
 * libtoolChecker.so, so the APK carries no library that would reveal the app.
 * JNI names therefore match org.telegram.messenger.partisan.rootbeer.RootBeerNative,
 * and the symbols are marked JNIEXPORT so they survive the build's
 * -fvisibility=hidden and stay resolvable via dlsym at call time.
 ****************************************************************************/

#include <jni.h>
#include <android/log.h>
#include <stdio.h>

#define  LOG_TAG    "RootBeer"
#define  LOGD(...)  if (DEBUG) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__);

static int DEBUG = 1;

static int exists(const char *fname) {
    FILE *file;
    if ((file = fopen(fname, "r"))) {
        LOGD("LOOKING FOR BINARY: %s PRESENT!!!", fname);
        fclose(file);
        return 1;
    }
    LOGD("LOOKING FOR BINARY: %s Absent :(", fname);
    return 0;
}

extern "C" {

JNIEXPORT void JNICALL
Java_org_telegram_messenger_partisan_rootbeer_RootBeerNative_setLogDebugMessages(JNIEnv *env, jobject thiz, jboolean debug) {
    DEBUG = debug ? 1 : 0;
}

JNIEXPORT jint JNICALL
Java_org_telegram_messenger_partisan_rootbeer_RootBeerNative_checkForRoot(JNIEnv *env, jobject thiz, jobjectArray pathsArray) {
    int binariesFound = 0;

    int stringCount = env->GetArrayLength(pathsArray);

    for (int i = 0; i < stringCount; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(pathsArray, i);
        const char *pathString = env->GetStringUTFChars(string, 0);

        binariesFound += exists(pathString);

        env->ReleaseStringUTFChars(string, pathString);
    }

    return binariesFound > 0;
}

}
