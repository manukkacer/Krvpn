/*
 * JNI Header for libxray.so (Xray-core)
 * Generated from XrayCore.kt native method declarations
 * Package: com.krvpn.app.nativebridge
 */

#ifndef XRAY_CORE_H
#define XRAY_CORE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Class: com.krvpn.app.nativebridge.XrayCore
// Method: nativeStart
// Signature: (Ljava/lang/String;)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_XrayCore_nativeStart(
    JNIEnv* env, jobject thiz, jstring configJson);

// Method: nativeStop
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_XrayCore_nativeStop(
    JNIEnv* env, jobject thiz);

// Method: nativeIsRunning
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_XrayCore_nativeIsRunning(
    JNIEnv* env, jobject thiz);

// Method: nativeGetStats
// Signature: ()[J
JNIEXPORT jlongArray JNICALL
Java_com_krvpn_app_nativebridge_XrayCore_nativeGetStats(
    JNIEnv* env, jobject thiz);

// Method: nativeTestConfig
// Signature: (Ljava/lang/String;)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_XrayCore_nativeTestConfig(
    JNIEnv* env, jobject thiz, jstring configJson);

#ifdef __cplusplus
}
#endif

#endif // XRAY_CORE_H
