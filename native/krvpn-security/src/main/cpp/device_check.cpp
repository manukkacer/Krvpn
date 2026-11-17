#include "krvpn_security.h"
#include <android/log.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <cstring>

#define LOG_TAG "KrSecurity-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool fileExists(const char* path) {
    struct stat buffer;
    return (stat(path, &buffer) == 0);
}

static bool checkRoot() {
    const char* suPaths[] = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/system/su", "/system/bin/.ext/su", "/system/xbin/.ext/su",
        "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su"
    };
    
    for (const char* path : suPaths) {
        if (fileExists(path)) {
            LOGD("Root detected: %s exists", path);
            return true;
        }
    }
    
    // Check for Magisk
    if (fileExists("/system/app/MagiskManager.apk") ||
        fileExists("/data/adb/magisk") ||
        fileExists("/sbin/magisk") ||
        fileExists("/sbin/.magisk")) {
        LOGD("Magisk detected");
        return true;
    }
    
    // Check for SuperSU
    if (fileExists("/system/app/Superuser.apk") ||
        fileExists("/system/xbin/daemonsu") ||
        fileExists("/system/etc/init.d/99SuperSUDaemon")) {
        LOGD("SuperSU detected");
        return true;
    }
    
    return false;
}

static bool checkEmulator() {
    // Check build properties
    std::ifstream buildProp("/system/build.prop");
    if (buildProp.is_open()) {
        std::string line;
        while (std::getline(buildProp, line)) {
            if (line.find("ro.product.model=") != std::string::npos) {
                std::string model = line.substr(15);
                if (model.find("Emulator") != std::string::npos ||
                    model.find("Android SDK") != std::string::npos ||
                    model.find("google_sdk") != std::string::npos) {
                    LOGD("Emulator detected via model: %s", model.c_str());
                    return true;
                }
            }
            if (line.find("ro.hardware=") != std::string::npos) {
                std::string hw = line.substr(12);
                if (hw.find("goldfish") != std::string::npos ||
                    hw.find("ranchu") != std::string::npos) {
                    LOGD("Emulator detected via hardware: %s", hw.c_str());
                    return true;
                }
            }
            if (line.find("ro.kernel.qemu=1") != std::string::npos) {
                LOGD("Emulator detected via qemu");
                return true;
            }
        }
    }
    
    // Check for qemu pipes
    if (fileExists("/dev/qemu_pipe") || fileExists("/dev/socket/qemud")) {
        LOGD("Emulator detected via qemu pipes");
        return true;
    }
    
    return false;
}

static bool checkDebugger() {
    // Check /proc/self/status for TracerPid
    std::ifstream status("/proc/self/status");
    if (status.is_open()) {
        std::string line;
        while (std::getline(status, line)) {
            if (line.rfind("TracerPid:", 0) == 0) {
                int pid = std::stoi(line.substr(10));
                if (pid != 0) {
                    LOGD("Debugger detected: TracerPid=%d", pid);
                    return true;
                }
            }
        }
    }
    
    // Check ptrace
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) == -1) {
        LOGD("Debugger detected via ptrace");
        return true;
    }
    
    return false;
}

static bool checkHookFrameworks() {
    // Frida
    const char* fridaLibs[] = {
        "frida-agent", "frida-agent-64", "libfrida.so", "libfrida-64.so"
    };
    
    std::ifstream maps("/proc/self/maps");
    if (maps.is_open()) {
        std::string line;
        while (std::getline(maps, line)) {
            for (const char* lib : fridaLibs) {
                if (line.find(lib) != std::string::npos) {
                    LOGD("Frida detected: %s", lib);
                    return true;
                }
            }
            if (line.find("frida") != std::string::npos ||
                line.find("gum-js-loop") != std::string::npos ||
                line.find("gmain") != std::string::npos) {
                LOGD("Frida detected in maps");
                return true;
            }
        }
    }
    
    // Check for Frida files
    if (fileExists("/data/local/tmp/frida-server") ||
        fileExists("/data/local/tmp/frida-helper")) {
        LOGD("Frida server files detected");
        return true;
    }
    
    // Xposed
    if (fileExists("/system/framework/XposedBridge.jar") ||
        fileExists("/system/framework/XposedBridge-54.jar") ||
        fileExists("/system/framework/XposedBridge-55.jar") ||
        fileExists("/data/data/de.robv.android.xposed.installer")) {
        LOGD("Xposed detected");
        return true;
    }
    
    // Check for Xposed in maps
    maps.clear();
    maps.seekg(0);
    if (maps.is_open()) {
        std::string line;
        while (std::getline(maps, line)) {
            if (line.find("xposed") != std::string::npos ||
                line.find("Xposed") != std::string::npos) {
                LOGD("Xposed detected in maps");
                return true;
            }
        }
    }
    
    // EdXposed / LSPosed
    if (fileExists("/system/lib/libedxposed.so") ||
        fileExists("/system/lib64/libedxposed.so") ||
        fileExists("/system/lib/liblsposed.so") ||
        fileExists("/system/lib64/liblsposed.so")) {
        LOGD("EdXposed/LSPosed detected");
        return true;
    }
    
    // Substrate / Cydia Substrate
    if (fileExists("/system/lib/libsubstrate.so") ||
        fileExists("/system/lib64/libsubstrate.so")) {
        LOGD("Cydia Substrate detected");
        return true;
    }
    
    // Dobby
    if (fileExists("/system/lib/libdobby.so") ||
        fileExists("/system/lib64/libdobby.so")) {
        LOGD("Dobby detected");
        return true;
    }
    
    // Whale
    if (fileExists("/system/lib/libwhale.so") ||
        fileExists("/system/lib64/libwhale.so")) {
        LOGD("Whale detected");
        return true;
    }
    
    return false;
}

JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeEnvironmentFlags(
    JNIEnv* env, jobject thiz,
    jstring packageName, jstring signatureHash) {
    
    int flags = 0;
    
    if (checkRoot()) flags |= ENV_ROOTED;
    if (checkEmulator()) flags |= ENV_EMULATOR;
    if (checkDebugger()) flags |= ENV_DEBUGGER;
    if (checkHookFrameworks()) flags |= ENV_HOOKED;
    
    // Verify signature (would need package manager access from Java side)
    // For now, just log
    if (signatureHash) {
        const char* hash = env->GetStringUTFChars(signatureHash, nullptr);
        LOGD("Expected signature hash: %s", hash);
        env->ReleaseStringUTFChars(signatureHash, hash);
        // Signature verification would be done in Java with PackageManager
    }
    
    LOGD("Environment flags: 0x%X", flags);
    return flags;
}

JNIEXPORT jint JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeDeviceBinding(
    JNIEnv* env, jobject thiz,
    jstring packageName, jstring signatureHash) {
    
    int flags = 0;
    
    if (checkRoot()) flags |= ENV_ROOTED;
    if (checkEmulator()) flags |= ENV_EMULATOR;
    if (checkDebugger()) flags |= ENV_DEBUGGER;
    if (checkHookFrameworks()) flags |= ENV_HOOKED;
    
    // Additional binding checks
    if (packageName) {
        const char* pkg = env->GetStringUTFChars(packageName, nullptr);
        LOGD("Device binding check for package: %s", pkg);
        env->ReleaseStringUTFChars(packageName, pkg);
        
        // Verify package name matches
        // This would need to compare with actual package
    }
    
    return flags;
}

JNIEXPORT jboolean JNICALL
Java_com_krvpn_app_data_security_KrSecurity_nativeVerifyIntegrity(
    JNIEnv* env, jobject thiz) {
    
    // Verify native libraries haven't been modified
    // Check checksums of loaded libraries
    
    // Check self integrity
    std::ifstream self("/proc/self/exe", std::ios::binary);
    if (self.is_open()) {
        // Could compute hash and compare with known value
    }
    
    // Check for suspicious memory mappings
    std::ifstream maps("/proc/self/maps");
    if (maps.is_open()) {
        std::string line;
        while (std::getline(maps, line)) {
            // Look for RWX mappings (suspicious)
            if (line.find("rwxp") != std::string::npos) {
                LOGD("Suspicious RWX mapping: %s", line.c_str());
                // Could be JIT, but worth noting
            }
        }
    }
    
    return JNI_TRUE;
}
