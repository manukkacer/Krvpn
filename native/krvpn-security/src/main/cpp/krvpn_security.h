#ifndef KRVPN_SECURITY_H
#define KRVPN_SECURITY_H

#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Environment flags (matching Kotlin constants)
#define ENV_ROOTED              (1 << 0)
#define ENV_EMULATOR            (1 << 1)
#define ENV_DEBUGGER            (1 << 2)
#define ENV_HOOKED              (1 << 3)
#define ENV_SIGNATURE_MISMATCH  (1 << 4)
#define ENV_NATIVE_TAMPERED     (1 << 5)
#define ENV_UNSAFE              (1 << 6)

/**
 * Native environment security checks
 * @param env JNI environment
 * @param thiz Calling object
 * @param packageName App package name
 * @param signatureHash Expected SHA-256 of signing certificate
 * @return Bitmask of detected issues
 */
JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEnvironmentFlags(
    JNIEnv* env, jobject thiz,
    jstring packageName, jstring signatureHash);

/**
 * Encrypt config data using AES-256-GCM with PBKDF2 key derivation
 * @param env JNI environment
 * @param thiz Calling object
 * @param data Config JSON data
 * @param password Password for key derivation (can be empty for master key)
 * @return Encrypted data: salt(32) + iv(12) + ciphertext+tag
 */
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEncryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray data, jstring password);

/**
 * Decrypt config data
 * @param env JNI environment
 * @param thiz Calling object
 * @param encryptedData Encrypted data (salt+iv+ciphertext+tag)
 * @param password Password for key derivation
 * @return Decrypted config JSON
 */
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDecryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray encryptedData, jstring password);

/**
 * Device binding check - verifies app integrity
 * @param env JNI environment
 * @param thiz Calling object
 * @param packageName App package name
 * @param signatureHash Expected SHA-256 of signing certificate
 * @return 0 if valid, non-zero error code
 */
JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDeviceBinding(
    JNIEnv* env, jobject thiz,
    jstring packageName, jstring signatureHash);

/**
 * Verify application integrity (code signature, native libs)
 * @param env JNI environment
 * @param thiz Calling object
 * @return true if integrity verified
 */
JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeVerifyIntegrity(
    JNIEnv* env, jobject thiz);

/**
 * Generate cryptographically secure random bytes
 * @param env JNI environment
 * @param thiz Calling object
 * @param length Number of bytes to generate
 * @return Random bytes
 */
JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeGenerateSecureRandom(
    JNIEnv* env, jobject thiz,
    jint length);

#ifdef __cplusplus
}
#endif

#endif // KRVPN_SECURITY_H
