cmake_minimum_required(VERSION 3.18.1)
project("krvpn-security" LANGUAGES CXX)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Android specific
if(ANDROID)
    set(CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION "clang")
    set(CMAKE_ANDROID_STL "c++_shared")
endif()

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Source files
set(SOURCES
    src/main/cpp/krvpn_security.cpp
    src/main/cpp/crypto.cpp
    src/main/cpp/device_check.cpp
    src/main/cpp/integrity.cpp
)

# Create shared library
add_library(krvpn-security SHARED ${SOURCES})

# Link libraries
target_link_libraries(krvpn-security
    android
    log
    ssl
    crypto
)

# Compiler flags
target_compile_options(krvpn-security PRIVATE
    -Wall
    -Wextra
    -Werror
    -fvisibility=hidden
    -fPIC
)

# Release optimizations
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(krvpn-security PRIVATE
        -O3
        -DNDEBUG
        -DRELEASE_BUILD
    )
    # Strip symbols
    set_target_properties(krvpn-security PROPERTIES
        LINK_FLAGS "-Wl,--strip-all"
    )
endif()

# Install
install(TARGETS krvpn-security
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
)
