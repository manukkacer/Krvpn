package com.krvpn.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.Json
import java.util.*

/**
 * KRVPN Configuration Model
 * Supports SSH, VMess, VLESS, Trojan, SlowDNS, and Load Balance
 */
@Serializable
data class KrConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ConfigType,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiredAt: Long? = null,
    val locked: Boolean = false,
    val blockRoot: Boolean = false,
    val preventReExport: Boolean = false,
    val requireImportPassword: Boolean = false,
    val checksum: String = "",

    // SSH Config
    val ssh: SshConfig? = null,

    // Xray Config (VMess, VLESS, Trojan)
    val xray: XrayConfig? = null,

    // SlowDNS Config
    val slowDns: SlowDnsConfig? = null,

    // Load Balance Config
    val loadBalance: LoadBalanceConfig? = null,

    // DNS Config (override)
    val dns: DnsConfig? = null,

    // Optional note/message
    val message: String = "",
    
    // Import password hash (for protected configs)
    val importPasswordHash: String = "",

    // Field locks for protection
    val fieldLocks: ConfigFieldLocks = ConfigFieldLocks()
) {
    @Transient
    val isExpired: Boolean
        get() = expiredAt != null && System.currentTimeMillis() > expiredAt!!

    @Transient
    val isValid: Boolean
        get() = when (type) {
            ConfigType.SSH -> ssh != null && ssh.isValid
            ConfigType.VMESS, ConfigType.VLESS, ConfigType.TROJAN -> xray != null && xray.isValid
            ConfigType.SLOWDNS -> slowDns != null && slowDns.isValid
            ConfigType.LOAD_BALANCE -> loadBalance != null && loadBalance.isValid
            else -> false
        }

    @Transient
    val displayName: String
        get() = if (name.isNotBlank()) name else "Unnamed ${type.name}"

    fun copyWithUpdatedTimestamp(): KrConfig {
        return copy(updatedAt = System.currentTimeMillis())
    }
}

enum class ConfigType {
    SSH, VMESS, VLESS, TROJAN, SLOWDNS, LOAD_BALANCE
}

/**
 * SSH Configuration
 */
@Serializable
data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "", // Encrypted in storage
    val payload: String = "",
    val sni: String = "",
    val payloadTemplateId: String = "",
    val useCompression: Boolean = true,
    val keepAliveInterval: Int = 30,
    val connectTimeout: Int = 10000,
    val reconnectAttempts: Int = 3,
    val reconnectDelay: Int = 5000
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port in 1..65535 && username.isNotBlank()
}

/**
 * Xray Configuration (VMess, VLESS, Trojan)
 */
@Serializable
data class XrayConfig(
    val protocol: XrayProtocol,
    val host: String = "",
    val port: Int = 443,
    val uuid: String = "", // UUID for VMess/VLESS, Password for Trojan
    val alterId: Int = 0, // VMess only
    val security: XraySecurity = XraySecurity.TLS,
    val network: XrayNetwork = XrayNetwork.TCP,
    val sni: String = "",
    val fingerprint: String = "",
    val path: String = "",
    val hostHeader: String = "",
    val flow: String = "", // VLESS only
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val grpcServiceName: String = "",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    val allowInsecure: Boolean = false
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port in 1..65535 && uuid.isNotBlank()
}

enum class XrayProtocol {
    VMESS, VLESS, TROJAN, SHADOWSOCKS
}

enum class XraySecurity {
    NONE, TLS, REALITY
}

enum class XrayNetwork {
    TCP, WS, GRPC, H2, QUIC, HTTPUPGRADE
}

/**
 * SlowDNS (DNSTT) Configuration
 */
@Serializable
data class SlowDnsConfig(
    val resolver: String = "1.1.1.1",
    val nameserver: String = "",
    val publicKey: String = "",
    val recordType: SlowDnsRecordType = SlowDnsRecordType.AUTO,
    val timeout: Int = 10000,
    val retries: Int = 3
) {
    val isValid: Boolean
        get() = resolver.isNotBlank() && publicKey.isNotBlank()
}

enum class SlowDnsRecordType {
    AUTO, TXT, NULL, NS, CNAME, MX, SRV, A, AAAA
}

/**
 * DNS Configuration
 */
@Serializable
data class DnsConfig(
    val servers: List<String> = emptyList(),
    val dohUrl: String = "",
    val dotHost: String = "",
    val blockIpv6: Boolean = false,
    val enableCache: Boolean = true
) {
    val effectiveServers: List<String>
        get() = if (servers.isNotEmpty()) servers else listOf("1.1.1.1", "8.8.8.8")
}

/**
 * Load Balance Configuration
 */
@Serializable
data class LoadBalanceConfig(
    val profileAId: String = "",
    val profileBId: String = "",
    val strategy: LoadBalanceStrategy = LoadBalanceStrategy.FAILOVER,
    val profileAWeight: Int = 50,
    val profileBWeight: Int = 50,
    val healthCheckInterval: Int = 30000,
    val failoverThreshold: Int = 3
) {
    val isValid: Boolean
        get() = profileAId.isNotBlank() && profileBId.isNotBlank() && profileAId != profileBId
}

enum class LoadBalanceStrategy {
    FAILOVER, ROUND_ROBIN, FASTEST, LEAST_CONNECTIONS, WEIGHTED
}

/**
 * Field-level locks for config protection
 */
@Serializable
data class ConfigFieldLocks(
    val lockServer: Boolean = false,
    val lockPort: Boolean = false,
    val lockCredentials: Boolean = false,
    val lockPayload: Boolean = false,
    val lockSni: Boolean = false,
    val lockTlsSettings: Boolean = false,
    val lockNetworkSettings: Boolean = false,
    val lockDnsSettings: Boolean = false,
    val lockSlowDnsSettings: Boolean = false,
    val lockLoadBalanceSettings: Boolean = false,
    val lockExpiry: Boolean = false,
    val lockMessage: Boolean = false
) {
    fun isAnyLocked(): Boolean = this::class.declaredMemberProperties.any { it.get(this) as Boolean }
    
    fun lockedCount(): Int = this::class.declaredMemberProperties.count { it.get(this) as Boolean }
}

/**
 * Application Settings
 */
@Serializable
data class KrSettings(
    val themeMode: Int = 0, // 0=system, 1=light, 2=dark
    val localeTag: String = "",
    val autoReconnect: Boolean = true,
    val batteryOptimizationRequested: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val logLevel: Int = 2, // 0=verbose, 1=debug, 2=info, 3=warn, 4=error
    val maxLogEntries: Int = 5000,
    val enablePacketCapture: Boolean = false,
    val dnsOverHttps: Boolean = false,
    val customDohUrl: String = "",
    val preferIpv6: Boolean = false,
    val mtu: Int = 1500,
    val tcpFastOpen: Boolean = false,
    val udpEnabled: Boolean = true,
    val bypassApps: List<String> = emptyList(),
    val routeExclusions: List<String> = emptyList()
)

/**
 * Session History Entry
 */
@Serializable
data class SessionHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val configId: String,
    val configName: String,
    val configType: ConfigType,
    val startTime: Long,
    val endTime: Long? = null,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val errorMessage: String = ""
)

enum class SessionStatus {
    CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, FAILED, HELD, COMPLETED
}

/**
 * Payload Template
 */
@Serializable
data class PayloadTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
