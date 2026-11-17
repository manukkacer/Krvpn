#!/bin/bash
# Build libxray.so from Xray-core using gomobile
# Run on machine with Go 1.22+ and gomobile

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")

# Install gomobile if needed
if ! command -v gomobile &> /dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    export PATH="$PATH:$(go env GOPATH)/bin"
    gomobile init
fi

# Clone Xray-core
cd /tmp
if [[ ! -d "Xray-core" ]]; then
    git clone --depth 1 https://github.com/XTLS/Xray-core.git
fi
cd Xray-core

# Create proper mobile bind file
cat > xray_mobile.go << 'GOEOF'
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	
	"github.com/xtls/xray-core/app/proxyman/command"
	"github.com/xtls/xray-core/main/distro/all"
	"google.golang.org/protobuf/proto"
)

var (
	coreInstance *Core
	coreMutex    sync.Mutex
)

type Core struct {
	server *all.Server
	config string
}

func Start(configJson string) string {
	coreMutex.Lock()
	defer coreMutex.Unlock()
	
	if coreInstance != nil {
		return "already running"
	}
	
	// Parse config
	var config map[string]interface{}
	if err := json.Unmarshal([]byte(configJson), &config); err != nil {
		return "invalid config: " + err.Error()
	}
	
	// Create Xray server
	server, err := all.NewServer(config)
	if err != nil {
		return "failed to create server: " + err.Error()
	}
	
	coreInstance = &Core{server: server, config: configJson}
	
	// Start in background
	go func() {
		if err := server.Start(context.Background()); err != nil {
			fmt.Printf("Xray server error: %v\n", err)
		}
	}()
	
	return "started"
}

func Stop() string {
	coreMutex.Lock()
	defer coreMutex.Unlock()
	
	if coreInstance == nil {
		return "not running"
	}
	
	if coreInstance.server != nil {
		coreInstance.server.Close()
	}
	coreInstance = nil
	return "stopped"
}

func IsRunning() bool {
	coreMutex.Lock()
	defer coreMutex.Unlock()
	return coreInstance != nil
}

func GetStats() string {
	coreMutex.Lock()
	defer coreMutex.Unlock()
	
	if coreInstance == nil || coreInstance.server == nil {
		return `{"up":0,"down":0}`
	}
	
	// Get stats from command service
	stats := coreInstance.server.GetStats()
	return fmt.Sprintf(`{"up":%d,"down":%d}`, stats.Up, stats.Down)
}

func TestConfig(configJson string) bool {
	var config map[string]interface{}
	return json.Unmarshal([]byte(configJson), &config) == nil
}

func main() {}
GOEOF

echo "Building libxray.so for all architectures..."

for ABI in "${ABIS[@]}"; do
    echo "Building for $ABI..."
    mkdir -p "$JNI_LIBS_DIR/$ABI"
    
    case $ABI in
        arm64-v8a) GOARCH=arm64 ;;
        armeabi-v7a) GOARCH=arm ;;
        x86_64) GOARCH=amd64 ;;
        x86) GOARCH=386 ;;
    esac
    
    GOOS=android GOARCH=$GOARCH gomobile bind -target=android \
        -o "$JNI_LIBS_DIR/$ABI/libxray.so" -v . 2>&1 | grep -E "(BUILD|error|warning)" | tail -5
    
    if [[ -f "$JNI_LIBS_DIR/$ABI/libxray.so" ]]; then
        SIZE=$(stat -c%s "$JNI_LIBS_DIR/$ABI/libxray.so")
        echo "  ✓ Built $JNI_LIBS_DIR/$ABI/libxray.so ($SIZE bytes)"
    else
        echo "  ✗ Failed to build for $ABI"
    fi
done

echo "libxray.so build complete!"
ls -lh "$JNI_LIBS_DIR"/*/libxray.so
