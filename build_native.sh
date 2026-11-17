#!/bin/bash
# Build script for KRVPN native libraries
# Run this script to compile all native libraries

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_DIR="$SCRIPT_DIR/native"
BUILD_DIR="$SCRIPT_DIR/build/native"
JNI_LIBS_DIR="$SCRIPT_DIR/app/src/main/jniLibs"

# Android NDK path (adjust as needed)
NDK_PATH="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/26.1.10909125}"
CMAKE_PATH="${ANDROID_CMAKE_HOME:-$HOME/Android/Sdk/cmake/3.18.1/bin/cmake}"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
API_LEVEL=24

echo "=== KRVPN Native Library Builder ==="
echo "NDK: $NDK_PATH"
echo "CMake: $CMAKE_PATH"
echo "Output: $JNI_LIBS_DIR"

# Build krvpn-security
echo ""
echo "=== Building krvpn-security ==="
for ABI in "${ABIS[@]}"; do
    echo "Building for $ABI..."
    BUILD_ABI_DIR="$BUILD_DIR/krvpn-security/$ABI"
    mkdir -p "$BUILD_ABI_DIR"
    cd "$BUILD_ABI_DIR"
    
    "$CMAKE_PATH" "$NATIVE_DIR/krvpn-security" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$API_LEVEL" \
        -DCMAKE_BUILD_TYPE=Release \
        -DANDROID_STL=c++_shared \
        -DANDROID_NDK="$NDK_PATH"
    
    cmake --build . --config Release -- -j$(nproc)
    
    # Copy to jniLibs
    mkdir -p "$JNI_LIBS_DIR/$ABI"
    cp libkrvpn-security.so "$JNI_LIBS_DIR/$ABI/"
    echo "  -> Copied to $JNI_LIBS_DIR/$ABI/libkrvpn-security.so"
done

echo ""
echo "=== Native library build complete ==="
echo "Libraries in $JNI_LIBS_DIR:"
find "$JNI_LIBS_DIR" -name "*.so" -type f | sort

echo ""
echo "NOTE: You still need to manually place these prebuilt libraries:"
echo "  - libxray.so (from Xray-core gomobile build)"
echo "  - libdnstt.so (from DNSTT gomobile build)"  
echo "  - libhev-socks5-tunnel.so (from HEV SOCKS5 Tunnel build)"
echo ""
echo "Place them in app/src/main/jniLibs/<abi>/ for each architecture"
