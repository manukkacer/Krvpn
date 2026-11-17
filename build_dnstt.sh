#!/bin/bash
# Build libdnstt.so from DNSTT using gomobile

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

# Clone DNSTT
cd /tmp
if [[ ! -d "dnstt" ]]; then
    git clone --depth 1 https://github.com/bamsoftware/dnstt.git
fi
cd dnstt/dnstt-client

# Create mobile bind file
cat > dnstt_mobile.go << 'GOEOF'
package main

import (
	"context"
	"fmt"
	"sync"
	
	"github.com/bamsoftware/dnstt/dnstt-client/client"
)

var (
	clientInstance *Client
	clientMutex    sync.Mutex
)

type Client struct {
	cancel context.CancelFunc
	conn   *client.Conn
	stats  Stats
}

type Stats struct {
	QueriesSent     int64
	ResponsesRecv   int64
	BytesUp         int64
	BytesDown       int64
}

func Start(resolver, nameserver, publicKey string, recordType int, localPort int) bool {
	clientMutex.Lock()
	defer clientMutex.Unlock()
	
	if clientInstance != nil {
		return false
	}
	
	// Map record type
	var rt client.RecordType
	switch recordType {
	case 0: rt = client.RecordTypeAuto
	case 1: rt = client.RecordTypeTXT
	case 2: rt = client.RecordTypeNULL
	case 3: rt = client.RecordTypeNS
	case 4: rt = client.RecordTypeCNAME
	case 5: rt = client.RecordTypeMX
	case 6: rt = client.RecordTypeSRV
	case 7: rt = client.RecordTypeA
	case 8: rt = client.RecordTypeAAAA
	default: rt = client.RecordTypeAuto
	}
	
	ctx, cancel := context.WithCancel(context.Background())
	
	conn, err := client.Dial(ctx, &client.Config{
		Resolver:     resolver,
		NameServer:   nameserver,
		PublicKey:    publicKey,
		RecordType:   rt,
		LocalPort:    localPort,
		Privileged:   false,
	})
	if err != nil {
		fmt.Printf("DNSTT dial error: %v\n", err)
		cancel()
		return false
	}
	
	clientInstance = &Client{cancel: cancel, conn: conn}
	
	// Monitor connection
	go func() {
		conn.Wait()
		clientMutex.Lock()
		clientInstance = nil
		clientMutex.Unlock()
	}()
	
	return true
}

func Stop() bool {
	clientMutex.Lock()
	defer clientMutex.Unlock()
	
	if clientInstance == nil {
		return false
	}
	
	clientInstance.cancel()
	clientInstance = nil
	return true
}

func IsRunning() bool {
	clientMutex.Lock()
	defer clientMutex.Unlock()
	return clientInstance != nil
}

func GetStats() string {
	clientMutex.Lock()
	defer clientMutex.Unlock()
	
	if clientInstance == nil {
		return `{"queries":0,"responses":0,"up":0,"down":0}`
	}
	
	// Get stats from connection
	s := clientInstance.conn.Stats()
	return fmt.Sprintf(`{"queries":%d,"responses":%d,"up":%d,"down":%d}`, 
		s.QueriesSent, s.ResponsesRecv, s.BytesUp, s.BytesDown)
}

func main() {}
GOEOF

echo "Building libdnstt.so for all architectures..."

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
        -o "$JNI_LIBS_DIR/$ABI/libdnstt.so" -v . 2>&1 | grep -E "(BUILD|error|warning)" | tail -5
    
    if [[ -f "$JNI_LIBS_DIR/$ABI/libdnstt.so" ]]; then
        SIZE=$(stat -c%s "$JNI_LIBS_DIR/$ABI/libdnstt.so")
        echo "  ✓ Built $JNI_LIBS_DIR/$ABI/libdnstt.so ($SIZE bytes)"
    else
        echo "  ✗ Failed to build for $ABI"
    fi
done

echo "libdnstt.so build complete!"
ls -lh "$JNI_LIBS_DIR"/*/libdnstt.so
