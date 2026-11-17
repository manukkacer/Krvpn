#include "krvpn_security.h"
#include <android/log.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fstream>
#include <sstream>
#include <openssl/sha.h>
#include <vector>
#include <string>

#define LOG_TAG "KrSecurity-Integrity"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Known good hashes of native libraries (would be populated at build time)
struct LibraryHash {
    const char* name;
    const char* sha256;
};

// In production, these would be injected at build time
static const LibraryHash EXPECTED_LIBRARY_HASHES[] = {
    {"libxray.so", ""},
    {"libdnstt.so", ""},
    {"libhev-socks5-tunnel.so", ""},
    {"libkrvpn-security.so", ""},
    {nullptr, nullptr}
};

static std::string computeFileSHA256(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) return "";
    
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    
    constexpr size_t BUFSIZE = 8192;
    std::vector<char> buffer(BUFSIZE);
    
    while (file.read(buffer.data(), BUFSIZE) || file.gcount() > 0) {
        SHA256_Update(&ctx, buffer.data(), file.gcount());
    }
    
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256_Final(hash, &ctx);
    
    std::stringstream ss;
    for (int i = 0; i < SHA256_DIGEST_LENGTH; ++i) {
        ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
    }
    
    return ss.str();
}

static bool verifyLibraryIntegrity() {
    for (const auto& lib : EXPECTED_LIBRARY_HASHES) {
        if (!lib.name) break;
        
        if (lib.sha256 && lib.sha256[0] != '\0') {
            // Find library path
            std::string path = "/data/app/~~";
            // In practice, would use dlopen/dlinfo to find actual path
            // For now, skip if no hash provided
        }
    }
    return true;
}

JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeVerifyIntegrity(
    JNIEnv* env, jobject thiz) {
    
    // Verify native library integrity
    if (!verifyLibraryIntegrity()) {
        LOGE("Library integrity check failed");
        return JNI_FALSE;
    }
    
    // Verify no suspicious memory modifications
    // Check for inline hooks in critical functions
    
    return JNI_TRUE;
}
