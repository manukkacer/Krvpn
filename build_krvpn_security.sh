#!/bin/bash
# Build libkrvpn-security.so from native/krvpn-security using CMake/NDK

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"
BUILD_DIR="$PROJECT_ROOT/build/native/krvpn-security"
NATIVE_SRC="$PROJECT_ROOT/native/krvpn-security"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
API_LEVEL=24

# Detect NDK
if [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then
    NDK_PATH="$ANDROID_NDK_HOME"
elif [[ -d "$HOME/Android/Sdk/ndk/26.1.10909125" ]]; then
    NDK_PATH="$HOME/Android/Sdk/ndk/26.1.10909125"
else
    echo "ERROR: Android NDK not found! Set ANDROID_NDK_HOME"
    exit 1
fi

# Detect CMake
if command -v cmake &> /dev/null; then
    CMAKE_BIN="cmake"
elif [[ -f "$HOME/Android/Sdk/cmake/3.22.1/bin/cmake" ]]; then
    CMAKE_BIN="$HOME/Android/Sdk/cmake/3.22.1/bin/cmake"
else
    echo "ERROR: CMake not found!"
    exit 1
fi

echo "Building libkrvpn-security.so for all architectures..."

for ABI in "${ABIS[@]}"; do
    echo "Building for $ABI..."
    BUILD_ABI_DIR="$BUILD_DIR/$ABI"
    mkdir -p "$BUILD_ABI_DIR"
    cd "$BUILD_ABI_DIR"
    
    "$CMAKE_BIN" "$NATIVE_SRC" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$API_LEVEL" \
        -DCMAKE_BUILD_TYPE=Release \
        -DANDROID_STL=c++_shared
    
    cmake --build . --config Release -- -j$(nproc)
    
    mkdir -p "$JNI_LIBS_DIR/$ABI"
    cp libkrvpn-security.so "$JNI_LIBS_DIR/$ABI/"
    
    if [[ -f "$JNI_LIBS_DIR/$ABI/libkrvpn-security.so" ]]; then
        SIZE=$(stat -c%s "$JNI_LIBS_DIR/$ABI/libkrvpn-security.so")
        echo "  ✓ Built $JNI_LIBS_DIR/$ABI/libkrvpn-security.so ($SIZE bytes)"
    else
        echo "  ✗ Failed to build for $ABI"
    fi
done

echo "libkrvpn-security.so build complete!"
ls -lh "$JNI_LIBS_DIR"/*/libkrvpn-security.so
