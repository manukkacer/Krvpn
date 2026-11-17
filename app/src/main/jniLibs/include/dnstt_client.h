/*
 * JNI Header for libdnstt.so (DNSTT SlowDNS)
 * Package: com.krvpn.app.nativebridge
 */

#ifndef DNSTT_CLIENT_H
#define DNSTT_CLIENT_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Class: com.krvpn.app.nativebridge.DnsttClient
// Method: nativeStart
// Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_DnsttClient_nativeStart(
    JNIEnv* env, jobject thiz,
    jstring resolver, jstring nameserver, jstring publicKey,
    jint recordType, jint localPort);

// Method: nativeStop
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_DnsttClient_nativeStop(
    JNIEnv* env, jobject thiz);

// Method: nativeIsRunning
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_DnsttClient_nativeIsRunning(
    JNIEnv* env, jobject thiz);

// Method: nativeGetStats
// Signature: ()[J
JNIEXPORT jlongArray JNICALL
Java_com_krvpn_app_nativebridge_DnsttClient_nativeGetStats(
    JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // DNSTT_CLIENT_H
