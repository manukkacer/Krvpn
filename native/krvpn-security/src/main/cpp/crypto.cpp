#include "krvpn_security.h"
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/kdf.h>
#include <openssl/sha.h>
#include <vector>
#include <cstring>

#define PBKDF2_ITERATIONS 100000
#define AES_KEY_SIZE 32      // 256 bits
#define GCM_IV_LENGTH 12     // 96 bits
#define GCM_TAG_LENGTH 16    // 128 bits
#define SALT_LENGTH 32

// Master key storage (in production, use Android Keystore via JNI)
static std::vector<uint8_t> g_master_key;
static bool g_master_key_initialized = false;

// Initialize master key from Android Keystore (called from Java)
extern "C" JNIEXPORT void JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeInitMasterKey(
    JNIEnv* env, jobject thiz, jbyteArray masterKey) {
    if (masterKey) {
        jsize len = env->GetArrayLength(masterKey);
        g_master_key.resize(len);
        env->GetByteArrayRegion(masterKey, 0, len, reinterpret_cast<jbyte*>(g_master_key.data()));
        g_master_key_initialized = true;
    }
}

// Derive key using PBKDF2-HMAC-SHA256
static bool deriveKeyPBKDF2(const std::string& password, const uint8_t* salt, size_t saltLen, uint8_t* outKey) {
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_HKDF, nullptr);
    if (!ctx) return false;

    if (EVP_PKEY_derive_init(ctx) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        return false;
    }

    if (EVP_PKEY_CTX_set_hkdf_md(ctx, EVP_sha256()) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        return false;
    }

    if (EVP_PKEY_CTX_set_hkdf_salt(ctx, salt, saltLen) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        return false;
    }

    if (EVP_PKEY_CTX_set_hkdf_key(ctx, reinterpret_cast<const uint8_t*>(password.data()), password.size()) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        return false;
    }

    // Add info parameter for domain separation
    const char* info = "KRVPN-Config-Encryption";
    if (EVP_PKEY_CTX_add_hkdf_info(ctx, reinterpret_cast<const uint8_t*>(info), strlen(info)) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        return false;
    }

    size_t keyLen = AES_KEY_SIZE;
    int result = EVP_PKEY_derive(ctx, outKey, &keyLen);
    EVP_PKEY_CTX_free(ctx);

    return result > 0 && keyLen == AES_KEY_SIZE;
}

// AES-256-GCM encryption
static bool aesGcmEncrypt(const uint8_t* key, const uint8_t* iv, const uint8_t* plaintext, size_t plaintextLen, uint8_t* ciphertext, uint8_t* tag) {
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return false;

    int len = 0, ciphertextLen = 0;
    bool success = false;

    if (EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) <= 0) goto cleanup;
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, GCM_IV_LENGTH, nullptr) <= 0) goto cleanup;
    if (EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, iv) <= 0) goto cleanup;

    if (EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, plaintextLen) <= 0) goto cleanup;
    ciphertextLen = len;

    if (EVP_EncryptFinal_ex(ctx, ciphertext + len, &len) <= 0) goto cleanup;
    ciphertextLen += len;

    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, GCM_TAG_LENGTH, tag) <= 0) goto cleanup;

    success = true;

cleanup:
    EVP_CIPHER_CTX_free(ctx);
    return success;
}

// AES-256-GCM decryption
static bool aesGcmDecrypt(const uint8_t* key, const uint8_t* iv, const uint8_t* ciphertext, size_t ciphertextLen, const uint8_t* tag, uint8_t* plaintext) {
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return false;

    int len = 0, plaintextLen = 0;
    bool success = false;

    if (EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) <= 0) goto cleanup;
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, GCM_IV_LENGTH, nullptr) <= 0) goto cleanup;
    if (EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv) <= 0) goto cleanup;
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, GCM_TAG_LENGTH, const_cast<uint8_t*>(tag)) <= 0) goto cleanup;

    if (EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, ciphertextLen) <= 0) goto cleanup;
    plaintextLen = len;

    if (EVP_DecryptFinal_ex(ctx, plaintext + len, &len) <= 0) goto cleanup;
    plaintextLen += len;

    success = true;

cleanup:
    EVP_CIPHER_CTX_free(ctx);
    return success;
}

JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEncryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray data, jstring password) {

    // Get input data
    jsize dataLen = env->GetArrayLength(data);
    std::vector<uint8_t> plaintext(dataLen);
    env->GetByteArrayRegion(data, 0, dataLen, reinterpret_cast<jbyte*>(plaintext.data()));

    // Get password (may be null/empty for master key)
    std::string pwd;
    if (password) {
        const char* pwdChars = env->GetStringUTFChars(password, nullptr);
        pwd = pwdChars;
        env->ReleaseStringUTFChars(password, pwdChars);
    }

    // Generate salt
    std::vector<uint8_t> salt(SALT_LENGTH);
    if (RAND_bytes(salt.data(), SALT_LENGTH) != 1) {
        return nullptr;
    }

    // Derive key
    std::vector<uint8_t> key(AES_KEY_SIZE);
    if (pwd.empty()) {
        if (!g_master_key_initialized || g_master_key.size() != AES_KEY_SIZE) {
            return nullptr;
        }
        key = g_master_key;
    } else {
        if (!deriveKeyPBKDF2(pwd, salt.data(), SALT_LENGTH, key.data())) {
            return nullptr;
        }
    }

    // Generate IV
    std::vector<uint8_t> iv(GCM_IV_LENGTH);
    if (RAND_bytes(iv.data(), GCM_IV_LENGTH) != 1) {
        return nullptr;
    }

    // Encrypt
    std::vector<uint8_t> ciphertext(dataLen + GCM_TAG_LENGTH);
    std::vector<uint8_t> tag(GCM_TAG_LENGTH);

    if (!aesGcmEncrypt(key.data(), iv.data(), plaintext.data(), plaintext.size(), ciphertext.data(), tag.data())) {
        return nullptr;
    }

    // Combine: salt + iv + ciphertext + tag
    std::vector<uint8_t> output;
    output.reserve(SALT_LENGTH + GCM_IV_LENGTH + dataLen + GCM_TAG_LENGTH);
    output.insert(output.end(), salt.begin(), salt.end());
    output.insert(output.end(), iv.begin(), iv.end());
    output.insert(output.end(), ciphertext.begin(), ciphertext.begin() + dataLen);
    output.insert(output.end(), tag.begin(), tag.end());

    jbyteArray result = env->NewByteArray(output.size());
    env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<jbyte*>(output.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDecryptConfig(
    JNIEnv* env, jobject thiz,
    jbyteArray encryptedData, jstring password) {

    // Get encrypted data
    jsize encLen = env->GetArrayLength(encryptedData);
    if (encLen < SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
        return nullptr;
    }

    std::vector<uint8_t> encrypted(encLen);
    env->GetByteArrayRegion(encryptedData, 0, encLen, reinterpret_cast<jbyte*>(encrypted.data()));

    // Get password
    std::string pwd;
    if (password) {
        const char* pwdChars = env->GetStringUTFChars(password, nullptr);
        pwd = pwdChars;
        env->ReleaseStringUTFChars(password, pwdChars);
    }

    // Extract components
    uint8_t* ptr = encrypted.data();
    uint8_t* salt = ptr; ptr += SALT_LENGTH;
    uint8_t* iv = ptr; ptr += GCM_IV_LENGTH;
    size_t ciphertextLen = encLen - SALT_LENGTH - GCM_IV_LENGTH - GCM_TAG_LENGTH;
    uint8_t* ciphertext = ptr; ptr += ciphertextLen;
    uint8_t* tag = ptr;

    // Derive key
    std::vector<uint8_t> key(AES_KEY_SIZE);
    if (pwd.empty()) {
        if (!g_master_key_initialized || g_master_key.size() != AES_KEY_SIZE) {
            return nullptr;
        }
        key = g_master_key;
    } else {
        if (!deriveKeyPBKDF2(pwd, salt, SALT_LENGTH, key.data())) {
            return nullptr;
        }
    }

    // Decrypt
    std::vector<uint8_t> plaintext(ciphertextLen);
    if (!aesGcmDecrypt(key.data(), iv, ciphertext, ciphertextLen, tag, plaintext.data())) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(plaintext.size());
    env->SetByteArrayRegion(result, 0, plaintext.size(), reinterpret_cast<jbyte*>(plaintext.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeGenerateSecureRandom(
    JNIEnv* env, jobject thiz, jint length) {
    if (length <= 0 || length > 4096) return nullptr;

    std::vector<uint8_t> random(length);
    if (RAND_bytes(random.data(), length) != 1) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(length);
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<jbyte*>(random.data()));
    return result;
}
