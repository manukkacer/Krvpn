/*
 * JNI Header for libkrvpn-security.so (KRVPN Security)
 * Package: com.krvpn.app.data.security
 */

#ifndef KRVPN_SECURITY_H
#define KRVPN_SECURITY_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Class: com.krvpn.app.data.security.KrSecurity
// Method: nativeEnvironmentFlags
// Signature: ()I
JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEnvironmentFlags(
    JNIEnv* env, jobject thiz);

// Method: nativeEncryptConfig
// Signature: ([BLjava/lang/String;)[B
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEncryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray data, jstring password);

// Method: nativeDecryptConfig
// Signature: ([BLjava/lang/String;)[B
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDecryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray data, jstring password);

// Method: nativeDeviceBinding
// Signature: (Ljava/lang/String;Ljava/lang/String;)I
JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDeviceBinding(
    JNIEnv* env, jobject thiz,
    jstring packageName, jstring signatureHash);

// Method: nativeVerifyIntegrity
// Signature: ()Z
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeVerifyIntegrity(
    JNIEnv* env, jobject thiz);

// Method: nativeInitMasterKey (for passing master key from Kotlin)
// Signature: ([B)V
JNIEXPORT void JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeInitMasterKey(
    JNIEnv* env, jobject thiz,
    jbyteArray masterKey);

// Method: nativeGenerateSecureRandom
// Signature: (I)[B
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeGenerateSecureRandom(
    JNIEnv* env, jobject thiz,
    jint length);

#ifdef __cplusplus
}
#endif

#endif // KRVPN_SECURITY_H
