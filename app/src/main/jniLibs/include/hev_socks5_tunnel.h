/*
 * JNI Header for libhev-socks5-tunnel.so (HEV SOCKS5 Tunnel)
 * Package: com.krvpn.app.nativebridge
 */

#ifndef HEV_SOCKS5_TUNNEL_H
#define HEV_SOCKS5_TUNNEL_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Class: com.krvpn.app.nativebridge.HevSocks5Tunnel
// Method: nativeStartService
// Signature: (Ljava/lang/String;I)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeStartService(
    JNIEnv* env, jobject thiz,
    jstring socksHost, jint socksPort);

// Method: nativeStopService
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeStopService(
    JNIEnv* env, jobject thiz);

// Method: nativeIsRunning
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeIsRunning(
    JNIEnv* env, jobject thiz);

// Method: nativeGetStats
// Signature: ()[J
JNIEXPORT jlongArray JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeGetStats(
    JNIEnv* env, jobject thiz);

// Method: nativeSetUdpRelay
// Signature: (Z)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeSetUdpRelay(
    JNIEnv* env, jobject thiz, jboolean enabled);

// Method: nativeSetTcpMss
// Signature: (I)Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_nativebridge_HevSocks5Tunnel_nativeSetTcpMss(
    JNIEnv* env, jobject thiz, jint mss);

#ifdef __cplusplus
}
#endif

#endif // HEV_SOCKS5_TUNNEL_H
