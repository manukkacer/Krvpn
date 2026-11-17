# KRVPN Build Guide

Complete step-by-step instructions to build KRVPN from source.

## Prerequisites

### System Requirements
- OS: Linux (Ubuntu 22.04+), macOS (12+), or Windows 10/11 with WSL2
- RAM: 8 GB minimum (16 GB recommended)
- Disk: 20 GB free space
- CPU: x86_64 or ARM64

### Required Software

| Tool | Version | Install Command |
|------|---------|-----------------|
| JDK | 17 (Temurin/OpenJDK) | sudo apt install openjdk-17-jdk |
| Android Studio | Koala 2024.1.2+ | Download from developer.android.com |
| Android SDK | API 35 (Android 15) | Via Android Studio SDK Manager |
| Android NDK | 26.1.10909125 | Via Android Studio SDK Manager |
| CMake | 3.18.1+ | Via Android Studio SDK Manager |
| Git | 2.30+ | sudo apt install git |
| Gradle | 8.5+ | Included in project (wrapper) |

## Step 1: Clone & Setup

```bash
git clone <your-repo-url> krvpn
cd krvpn
ls -la
```

## Step 2: Generate Release Keystore

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/krvpn-release.jks -alias krvpn \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass YOUR_KEYSTORE_PASSWORD -keypass YOUR_KEY_PASSWORD \
  -dname "CN=KRVPN, OU=KRVPN, O=KRVPN, L=YourCity, ST=YourState, C=US"

cat > local.properties << 'EOF'
sdk.dir=/home/$USER/Android/Sdk
ndk.dir=/home/$USER/Android/Sdk/ndk/26.1.10909125
keystore.path=../keystore/krvpn-release.jks
keystore.password=YOUR_KEYSTORE_PASSWORD
key.alias=krvpn
key.password=YOUR_KEY_PASSWORD
EOF
```

## Step 3: Configure Native Libraries

### Required Native Libraries (place in app/src/main/jniLibs/<abi>/)

| Library | Source | Build Method |
|---------|--------|--------------|
| libxray.so | Xray-core | gomobile bind |
| libdnstt.so | DNSTT | gomobile bind |
| libhev-socks5-tunnel.so | HEV SOCKS5 Tunnel | CMake/NDK |
| libkrvpn-security.so | native/krvpn-security/ | Build via script |

### Build libkrvpn-security.so

```bash
chmod +x build_native.sh
./build_native.sh
```

### Building Xray-core (libxray.so)

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
git clone https://github.com/XTLS/Xray-core.git
cd Xray-core
# Create gomobile bind target and build
gomobile bind -target=android -o ../app/src/main/jniLibs/arm64-v8a/libxray.so -v .
```

### Building DNSTT (libdnstt.so)

```bash
git clone https://github.com/bamsoftware/dnstt.git
cd dnstt/dnstt-client
# Create mobile wrapper and build
gomobile bind -target=android -o ../app/src/main/jniLibs/arm64-v8a/libdnstt.so .
```

### Building HEV SOCKS5 Tunnel (libhev-socks5-tunnel.so)

```bash
git clone https://github.com/heiher/hev-socks5-tunnel.git
cd hev-socks5-tunnel
mkdir build-android && cd build-android
cmake -DCMAKE_TOOLCHAIN_FILE=$NDK_PATH/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 ..
make -j$(nproc)
cp libhev-socks5-tunnel.so ../../app/src/main/jniLibs/arm64-v8a/
```

## Step 4: Build Android App

```bash
# Debug
./gradlew assembleDebug

# Release (signed)
./gradlew assembleRelease

# AAB for Play Store
./gradlew bundleRelease

# Clean build
./gradlew clean assembleRelease
```

## Step 5: Install & Test

```bash
# Debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release
adb install -r app/build/outputs/apk/release/app-release.apk
```

Test checklist:
- Create SSH config and connect
- Import VMess/VLESS/Trojan link and connect
- Test SlowDNS config
- Test Load Balance
- Test Auto-reconnect (toggle airplane mode)
- Test Diagnostics screen
- Test Settings (theme, language, battery optimization)

## Step 6: Customize for Production

### 1. Update Release Certificate Hash (Security)
Edit app/src/main/java/com/krvpn/app/data/security/KrSecurity.kt:
```kotlin
val expectedHashes = setOf(
    "YOUR_ACTUAL_SHA256_HERE"  // Get via: keytool -list -v -keystore keystore/krvpn-release.jks
)
```

### 2. Update API Endpoint
Edit app/src/main/java/com/krvpn/app/di/NetworkModule.kt:
```kotlin
.baseUrl("https://your-api.krvpn.com/")
```

### 3. Update Branding
- app/src/main/res/values/strings.xml - App name, links, contact info
- app/src/main/res/values/colors.xml - Brand colors
- app/src/main/res/drawable/ic_krvpn_logo.xml - App icon
- app/src/main/AndroidManifest.xml - Deep link scheme (krvpn://)

### 4. Add Privacy Policy & Terms URLs
```xml
<string name="about_privacy_policy_url">https://krvpn.example.com/privacy</string>
<string name="about_terms_url">https://krvpn.example.com/terms</string>
```

## Troubleshooting

| Error | Solution |
|-------|----------|
| NDK not found | Set ndk.dir in local.properties or install via SDK Manager |
| CMake not found | Install CMake via SDK Manager |
| libxray.so not found | Place prebuilt .so in jniLibs/<abi>/ |
| UnsatisfiedLinkError | Wrong architecture .so; ensure all 4 ABIs have all 4 libs |
| Keystore not found | Check local.properties paths and passwords |
| Manifest merger failed | Check AndroidManifest.xml for conflicts |

Runtime errors:
| Error | Solution |
|-------|----------|
| VPN permission denied | User must grant permission in system dialog |
| Native library not loaded | .so files missing or wrong ABI |
| Config encryption failed | Check KrSecurity initialization; verify keystore |
| Connection fails | Check native libs (Xray/DNSTT/HEV) are working; check logs |

Debugging:
```bash
adb logcat -s KrApplication:D TunnelController:D KrVpnService:D KrSecurity:D
adb logcat | grep -i "loadlibrary\|unsatisfiedlink"
```

## Release Checklist

- [ ] Release keystore generated and backed up securely
- [ ] local.properties NOT committed (in .gitignore)
- [ ] Release certificate SHA-256 added to KrSecurity.kt
- [ ] Privacy Policy URL added and accessible
- [ ] Terms of Service URL added and accessible
- [ ] Data Safety Form completed in Play Console
- [ ] App tested on Android 10-15
- [ ] Tested on ARM64 and ARM32 devices
- [ ] VPN permission handling works correctly
- [ ] Battery optimization exemption requested properly
- [ ] Foreground service notification shows correctly
- [ ] Auto-start on boot works
- [ ] All config import/export formats work
- [ ] ProGuard/R8 rules tested (release build runs)
- [ ] App size optimized (check APK Analyzer)
- [ ] Screenshots taken for Play Store listing
- [ ] Release notes written

## CI/CD (GitHub Actions)

See .github/workflows/build.yml template in the repository.

## Support

- Issues: GitHub Issues
- Telegram: @KRVPN_Official (create your own)
- Documentation: This guide + README.md

Happy Building!
