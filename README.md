# KRVPN - Modern Android VPN Client

A privacy-focused, open-source VPN client for Android supporting SSH, VMess, VLESS, Trojan, and SlowDNS protocols.

## Features

- **Multi-Protocol Support**: SSH, VMess, VLESS, Trojan (via Xray-core), SlowDNS (via DNSTT)
- **Advanced Security**: Config encryption, field-level locking, anti-tamper, device binding
- **Load Balancing**: Combine two profiles with failover, round-robin, fastest, least-connections, weighted strategies
- **Privacy First**: No accounts, no analytics, no tracking, local-only storage
- **Modern UI**: Material 3, Jetpack Compose, dark/light theme
- **Battery Optimized**: Foreground service with proper battery exemption handling

## Architecture

```
KRVPN App
├── UI Layer (Compose + Material3)
├── Domain Layer (TunnelController, UseCases)
├── Data Layer (ConfigRepository, SettingsRepository, KrSecurity)
├── Native Bridges (JNI)
│   ├── XrayCore (libxray.so) - VMess/VLESS/Trojan
│   ├── DnsttClient (libdnstt.so) - SlowDNS
│   ├── HevSocks5Tunnel (libhev-socks5-tunnel.so) - TUN↔SOCKS5
│   └── SshTunnel (JSch) - SSH
├── Services
│   ├── KrVpnService (VpnService)
│   └── KrForegroundService (Persistent notification)
└── Native Security (libkrvpn-security.so)
    ├── Config encryption (AES-256-GCM + PBKDF2)
    ├── Device binding & anti-tamper
    └── Integrity verification
```

## Building

### Prerequisites

- Android Studio Koala (2024.1.2) or later
- JDK 17
- Android SDK 35
- NDK 26.1.10909125
- CMake 3.18.1+

### Native Libraries

Prebuilt native libraries are required in `app/src/main/jniLibs/`:
- `libxray.so` - Compile from [Xray-core](https://github.com/XTLS/Xray-core) using gomobile
- `libdnstt.so` - Compile from [DNSTT](https://github.com/bamsoftware/dnstt) using gomobile
- `libhev-socks5-tunnel.so` - Compile from [HEV SOCKS5 Tunnel](https://github.com/heiher/hev-socks5-tunnel)
- `libkrvpn-security.so` - Build from `native/krvpn-security/` using CMake

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore in local.properties)
./gradlew assembleRelease
```

### Keystore Setup

Create `local.properties` with:
```properties
keystore.path=../keystore/krvpn-release.jks
keystore.password=YOUR_PASSWORD
key.alias=krvpn
key.password=YOUR_PASSWORD
```

Generate keystore:
```bash
keytool -genkeypair -v -keystore krvpn-release.jks -alias krvpn \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD \
  -dname "CN=KRVPN, OU=KRVPN, O=KRVPN, L=City, ST=State, C=US"
```

## Project Structure

```
krvpn/
├── app/                          # Main Android app module
│   ├── src/main/
│   │   ├── java/com/krvpn/app/
│   │   │   ├── di/               # Hilt DI modules
│   │   │   ├── data/             # Data layer
│   │   │   │   ├── local/        # ConfigRepository, SettingsRepository
│   │   │   │   ├── model/        # KrConfig, KrSettings, etc.
│   │   │   │   ├── remote/       # API interfaces
│   │   │   │   └── security/     # KrSecurity (encryption, anti-tamper)
│   │   │   ├── domain/           # Business logic
│   │   │   │   ├── tunnel/       # TunnelController
│   │   │   │   └── usecase/      # Use cases
│   │   │   ├── nativebridge/     # JNI wrappers
│   │   │   ├── presentation/     # UI layer
│   │   │   │   ├── ui/           # Compose screens
│   │   │   │   └── viewmodel/    # ViewModels
│   │   │   └── service/          # VpnService, ForegroundService
│   │   ├── jniLibs/              # Native libraries (arm64-v8a, armeabi-v7a)
│   │   ├── res/                  # Resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── native/
│   └── krvpn-security/           # Custom native security library
│       ├── CMakeLists.txt
│       └── src/main/cpp/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Configuration

### Supported Config Formats

- **Import**: `vmess://`, `vless://`, `trojan://`, `ssh://`, `krvpn://`, JSON files
- **Export**: `krvpn://` links, `.krvpn` encrypted files

### Config Fields

- **SSH**: Host, port, username, password, payload, SNI, payload template
- **Xray (VMess/VLESS/Trojan)**: UUID, host, port, network (TCP/WS/gRPC/H2/QUIC), TLS/REALITY, flow, Mux, fingerprint
- **SlowDNS**: Resolver, nameserver, public key, record type (TXT/NULL/AUTO)
- **Protection**: Field locks, prevent re-export, import password, root blocking, expiry

## Security

- **Config Encryption**: AES-256-GCM with PBKDF2 (100k iterations)
- **Master Key**: Android Keystore (hardware-backed when available)
- **Anti-Tamper**: Root detection, emulator detection, debugger detection, hook framework detection (Frida, Xposed, etc.)
- **Device Binding**: Signature verification, package name verification
- **Integrity**: Native library checksum verification

## License

- **App Code**: MIT License
- **Xray-core**: MPL-2.0
- **DNSTT**: CC0 / BSD
- **HEV SOCKS5 Tunnel**: LGPL-3.0
- **JSch**: BSD-style

## Contributing

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Open Pull Request

## Privacy

KRVPN collects **no data**. No accounts, no analytics, no crash reporting (unless explicitly enabled). All configs and logs stored locally on device.

## Disclaimer

This software is for educational and legitimate privacy purposes only. Users are responsible for complying with local laws and regulations. The authors are not liable for any misuse.
