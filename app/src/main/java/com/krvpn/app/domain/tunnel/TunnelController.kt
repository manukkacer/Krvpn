package com.krvpn.app.domain.tunnel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.krvpn.app.data.model.*
import com.krvpn.app.nativebridge.*
import com.krvpn.app.service.KrVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tunnel Controller - Orchestrates VPN connection lifecycle
 * Coordinates: VpnService, SSH/Xray/SlowDNS, HEV SOCKS5 Tunnel
 */
class TunnelController(
    private val context: Context,
    private val vpnService: KrVpnService,
    private val configRepository: com.krvpn.app.data.local.ConfigRepository,
    private val settingsRepository: com.krvpn.app.data.local.SettingsRepository,
    private val security: com.krvpn.app.data.security.KrSecurity
) {

    private const val TAG = "TunnelController"
    private const val VPN_MTU = 1500
    private const val TUN_IPV4 = "10.8.0.2/32"
    private const val TUN_IPV6 = "fd00:1fd::2/128"
    private const val DNS_MAPPED = "198.18.0.2"
    private const val INTERNAL_ROUTE = "10.255.0.0/16"

    // State
    private val _state = MutableStateFlow(TunnelState.DISCONNECTED)
    val state = _state.asStateFlow()

    private val _stats = MutableStateFlow(TunnelStats(0L, 0L, 0L))
    val stats = _stats.asStateFlow()

    private val _currentConfig = MutableStateFlow<KrConfig?>(null)
    val currentConfig = _currentConfig.asStateFlow()

    // Components
    private var xrayCore: XrayCore? = null
    private var dnsttClient: DnsttClient? = null
    private var hevTunnel: HevSocks5Tunnel? = null
    private var sshTunnel: SshTunnel? = null

    // Lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)
    private val reconnectAttempts = AtomicReference(0)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var protectSockets: ((String, Int) -> Boolean)? = null

    // Config
    private var currentSocksPort = 1080
    private var currentSocksHost = "127.0.0.1"

    init {
        initComponents()
    }

    private fun initComponents() {
        xrayCore = XrayCore.getInstance(context)
        dnsttClient = DnsttClient.getInstance(context)
        hevTunnel = HevSocks5Tunnel.getInstance(context)
        sshTunnel = SshTunnel.getInstance(context)
    }

    // ============ PUBLIC API ============

    /**
     * Start VPN tunnel with given config
     */
    suspend fun start(config: KrConfig): Boolean {
        if (isRunning.get()) {
            Log.w(TAG, "Tunnel already running, stopping first")
            stop()
        }

        _state.value = TunnelState.CONNECTING
        _currentConfig.value = config

        return try {
            // Prepare VPN interface
            val fd = prepareVpn(config)
            if (fd == null) {
                _state.value = TunnelState.ERROR("VPN preparation failed")
                return false
            }
            vpnInterface = fd

            // Start upstream proxy based on config type
            val proxyStarted = startUpstreamProxy(config)
            if (!proxyStarted) {
                _state.value = TunnelState.ERROR("Failed to start upstream proxy")
                cleanup()
                return false
            }

            // Start HEV SOCKS5 Tunnel (TUN -> SOCKS5)
            val tunnelStarted = hevTunnel?.start(currentSocksHost, currentSocksPort) ?: false
            if (!tunnelStarted) {
                _state.value = TunnelState.ERROR("Failed to start SOCKS5 tunnel")
                cleanup()
                return false
            }

            // Configure HEV tunnel
            hevTunnel?.setUdpRelay(settingsRepository.getUdpEnabled())
            hevTunnel?.setTcpMss(settingsRepository.getMtu() - 40)

            // Start stats collection
            startStatsCollection()

            isRunning.set(true)
            reconnectAttempts.set(0)
            _state.value = TunnelState.CONNECTED
            
            Log.i(TAG, "Tunnel started successfully with ${config.type} config: ${config.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tunnel", e)
            _state.value = TunnelState.ERROR(e.message ?: "Unknown error")
            cleanup()
            false
        }
    }

    /**
     * Stop VPN tunnel
     */
    suspend fun stop() {
        isRunning.set(false)
        scope.cancel()
        cleanup()
        _state.value = TunnelState.DISCONNECTED
        _currentConfig.value = null
        Log.i(TAG, "Tunnel stopped")
    }

    /**
     * Hold tunnel (pause traffic, keep VPN interface)
     */
    suspend fun hold(reason: String = "User requested hold") {
        if (!isRunning.get()) return
        
        Log.i(TAG, "Holding tunnel: $reason")
        _state.value = TunnelState.HELD(reason)
        
        // Stop upstream proxies but keep VPN interface
        stopUpstreamProxies()
        hevTunnel?.stop()
    }

    /**
     * Resume from hold
     */
    suspend fun resume() {
        val config = _currentConfig.value ?: return
        if (_state.value !is TunnelState.HELD) return

        Log.i(TAG, "Resuming tunnel")
        _state.value = TunnelState.CONNECTING
        
        val proxyStarted = startUpstreamProxy(config)
        if (!proxyStarted) {
            _state.value = TunnelState.ERROR("Failed to resume upstream proxy")
            return
        }

        val tunnelStarted = hevTunnel?.start(currentSocksHost, currentSocksPort) ?: false
        if (!tunnelStarted) {
            _state.value = TunnelState.ERROR("Failed to resume SOCKS5 tunnel")
            return
        }

        _state.value = TunnelState.CONNECTED
    }

    /**
     * Trigger reconnect (e.g., on network change)
     */
    fun triggerReconnect() {
        if (isReconnecting.getAndSet(true)) return
        
        scope.launch {
            try {
                val config = _currentConfig.value ?: return@launch
                val maxAttempts = 5
                
                while (reconnectAttempts.getAndIncrement() < maxAttempts && isRunning.get()) {
                    _state.value = TunnelState.RECONNECTING(reconnectAttempts.get())
                    Log.i(TAG, "Reconnect attempt ${reconnectAttempts.get()}/$maxAttempts")
                    
                    delay(3000 * reconnectAttempts.get()) // Exponential backoff
                    
                    val fd = prepareVpn(config)
                    if (fd != null) {
                        vpnInterface?.close()
                        vpnInterface = fd
                        
                        val proxyStarted = startUpstreamProxy(config)
                        if (proxyStarted && (hevTunnel?.start(currentSocksHost, currentSocksPort) ?: false)) {
                            _state.value = TunnelState.CONNECTED
                            reconnectAttempts.set(0)
                            isReconnecting.set(false)
                            return@launch
                        }
                    }
                }
                
                _state.value = TunnelState.ERROR("Reconnect failed after $maxAttempts attempts")
                stop()
            } finally {
                isReconnecting.set(false)
            }
        }
    }

    // ============ PRIVATE METHODS ============

    private fun prepareVpn(config: KrConfig): ParcelFileDescriptor? {
        return try {
            val builder = VpnService.Builder()
                .setSession("KRVPN - ${config.name}")
                .setMtu(settingsRepository.getMtu())
                .addAddress(TUN_IPV4)
                .addRoute("0.0.0.0", 0)

            // IPv6 if supported
            if (supportsIpv6()) {
                builder.addAddress(TUN_IPV6)
                builder.addRoute("::", 0)
            }

            // DNS Configuration
            val dnsServers = getEffectiveDnsServers(config)
            val useMappedDns = shouldUseMappedDns(config)
            
            if (useMappedDns) {
                builder.addDnsServer(DNS_MAPPED)
            } else {
                dnsServers.forEach { builder.addDnsServer(it) }
            }

            // Exclude this app's traffic
            builder.addDisallowedApplication(context.packageName)

            // Add internal route
            builder.addRoute(INTERNAL_ROUTE)

            // Exclude bypass apps
            getBypassApps().forEach { builder.addDisallowedApplication(it) }

            // Establish
            val fd = builder.establish()
            if (fd == null) {
                Log.e(TAG, "VPN permission denied or builder returned null")
            }
            fd
        } catch (e: Exception) {
            Log.e(TAG, "VPN preparation failed", e)
            null
        }
    }

    private fun startUpstreamProxy(config: KrConfig): Boolean {
        return when (config.type) {
            ConfigType.SSH -> startSshProxy(config)
            ConfigType.VMESS, ConfigType.VLESS, ConfigType.TROJAN -> startXrayProxy(config)
            ConfigType.SLOWDNS -> startSlowDnsProxy(config)
            ConfigType.LOAD_BALANCE -> startLoadBalanceProxy(config)
            else -> false
        }
    }

    private fun startSshProxy(config: KrConfig): Boolean {
        val ssh = config.ssh ?: return false
        val port = findFreePort(1080..1090) ?: return false
        
        val started = sshTunnel?.start(ssh, port) ?: false
        if (started) {
            currentSocksPort = sshTunnel?.getSocksPort() ?: port
            currentSocksHost = "127.0.0.1"
        }
        return started
    }

    private fun startXrayProxy(config: KrConfig): Boolean {
        val xray = config.xray ?: return false
        val port = findFreePort(1080..1090) ?: return false
        
        // Build Xray config JSON
        val xrayJson = XrayCore.ConfigBuilder.buildXrayConfig(xray, port)
        
        val started = xrayCore?.nativeStart(xrayJson) ?: false
        if (started) {
            currentSocksPort = port
            currentSocksHost = "127.0.0.1"
        }
        return started
    }

    private fun startSlowDnsProxy(config: KrConfig): Boolean {
        val slowDns = config.slowDns ?: return false
        val port = findFreePort(1080..1090) ?: return false
        
        val started = dnsttClient?.start(slowDns, port) ?: false
        if (started) {
            currentSocksPort = port
            currentSocksHost = "127.0.0.1"
        }
        return started
    }

    private fun startLoadBalanceProxy(config: KrConfig): Boolean {
        val lb = config.loadBalance ?: return false
        // TODO: Implement Load Balance - start two upstream proxies
        // For now, fallback to profile A
        val profileA = configRepository.getById(lb.profileAId)
        if (profileA != null) {
            return startUpstreamProxy(profileA)
        }
        return false
    }

    private fun stopUpstreamProxies() {
        sshTunnel?.stop()
        xrayCore?.nativeStop()
        dnsttClient?.stop()
    }

    private fun cleanup() {
        stopUpstreamProxies()
        hevTunnel?.stop()
        
        vpnInterface?.close()
        vpnInterface = null
        
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun findFreePort(range: IntRange): Int? {
        return range.firstOrNull { port ->
            try {
                java.net.ServerSocket(port).use { it.setReuseAddress(true) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun supportsIpv6(): Boolean {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            return interfaces.any { it.inetAddresses.any { it is java.net.Inet6Address } }
        } catch (e: Exception) {
            return false
        }
    }

    private fun getEffectiveDnsServers(config: KrConfig): List<String> {
        val customDns = config.dns?.effectiveServers
        if (customDns != null && customDns.isNotEmpty()) return customDns
        
        val settingsDns = if (settingsRepository.getDnsOverHttps()) {
            settingsRepository.getCustomDohUrl().takeIf { it.isNotBlank() }?.let { listOf(it) } ?: listOf("https://dns.google/dns-query")
        } else {
            emptyList<String>()
        }
        
        return if (settingsDns.isNotEmpty()) settingsDns else listOf("1.1.1.1", "8.8.8.8")
    }

    private fun shouldUseMappedDns(config: KrConfig): Boolean {
        // Use mapped DNS (198.18.0.2) when upstream is SOCKS5 and supports UDP
        return when (config.type) {
            ConfigType.SSH -> true // SSH SOCKS5 typically supports UDP
            ConfigType.VMESS, ConfigType.VLESS, ConfigType.TROJAN -> true // Xray SOCKS5 supports UDP
            ConfigType.SLOWDNS -> false // SlowDNS uses its own DNS
            else -> false
        }
    }

    private fun getBypassApps(): List<String> {
        // Could be loaded from settings
        return emptyList()
    }

    private fun startStatsCollection() {
        scope.launch {
            while (isRunning.get()) {
                delay(1000)
                
                var tx = 0L
                var rx = 0L
                var duration = System.currentTimeMillis() - (_currentConfig.value?.createdAt ?: System.currentTimeMillis())
                
                sshTunnel?.let { tx += it.getStats()[0]; rx += it.getStats()[1] }
                xrayCore?.let { 
                    val s = it.nativeGetStats()
                    tx += s[0]; rx += s[1] 
                }
                dnsttClient?.let { 
                    val s = it.nativeGetStats()
                    tx += s[2]; rx += s[3] 
                }
                hevTunnel?.let { 
                    val s = it.nativeGetStats()
                    tx += s[0]; rx += s[1] 
                }

                _stats.value = TunnelStats(tx, rx, duration)
            }
        }
    }

    // ============ DATA CLASSES ============

    sealed interface TunnelState {
        data object DISCONNECTED : TunnelState
        data object CONNECTING : TunnelState
        data class CONNECTED(val since: Long = System.currentTimeMillis()) : TunnelState
        data class HELD(val reason: String) : TunnelState
        data class RECONNECTING(val attempt: Int) : TunnelState
        data class ERROR(val message: String) : TunnelState
    }

    data class TunnelStats(
        val txBytes: Long,
        val rxBytes: Long,
        val durationMs: Long
    ) {
        val totalBytes: Long get() = txBytes + rxBytes
        val txSpeed: Double get() = if (durationMs > 0) txBytes / (durationMs / 1000.0) else 0.0
        val rxSpeed: Double get() = if (durationMs > 0) rxBytes / (durationMs / 1000.0) else 0.0
    }
}
