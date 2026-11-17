package com.krvpn.app.nativebridge

import android.content.Context
import android.util.Log
import com.krvpn.app.data.model.XrayConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Xray Core Native Bridge
 * Wraps libxray.so for VMess/VLESS/Trojan/Shadowsocks
 */
class XrayCore private constructor(private val context: Context) {

    companion object {
        private const val TAG = "XrayCore"
        private const val LIB_NAME = "xray"
        @Volatile private var instance: XrayCore? = null
        private val initialized = AtomicBoolean(false)

        fun getInstance(context: Context): XrayCore {
            return instance ?: synchronized(this) {
                instance ?: XrayCore(context.applicationContext).also { instance = it }
            }
        }

        fun isLoaded(): Boolean = initialized.get()
    }

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        try {
            System.loadLibrary(LIB_NAME)
            initialized.set(true)
            Log.i(TAG, "libxray.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libxray.so", e)
            initialized.set(false)
        }
    }

    // ============ NATIVE METHODS ============

    /**
     * Start Xray core with JSON config
     * @return true if started successfully
     */
    external fun nativeStart(configJson: String): Boolean

    /**
     * Stop Xray core
     */
    external fun nativeStop(): Boolean

    /**
     * Check if Xray is running
     */
    external fun nativeIsRunning(): Boolean

    /**
     * Get traffic statistics
     * @return long array [uploadBytes, downloadBytes]
     */
    external fun nativeGetStats(): LongArray

    /**
     * Test config validity
     * @return true if config is valid
     */
    external fun nativeTestConfig(configJson: String): Boolean

    // ============ KOTLIN WRAPPER METHODS ============

    /**
     * Start Xray with typed config
     */
    suspend fun start(config: XrayConfig): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        val json = Json.encodeToString(config)
        nativeStart(json)
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext true
        nativeStop()
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        initialized.get() && nativeIsRunning()
    }

    suspend fun getStats(): LongArray = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext longArrayOf(0L, 0L)
        nativeGetStats()
    }

    suspend fun testConfig(config: XrayConfig): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        val json = Json.encodeToString(config)
        nativeTestConfig(json)
    }

    // ============ CONFIG BUILDER HELPER ============

    companion object ConfigBuilder {
        /**
         * Build Xray JSON config from KrConfig
         */
        fun buildXrayConfig(xray: XrayConfig, inboundPort: Int = 1080): String {
            // This builds the Xray JSON configuration
            // See: https://xtls.github.io/config/
            return buildJson(xray, inboundPort)
        }

        private fun buildJson(config: XrayConfig, inboundPort: Int): String {
            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"log\": { \"loglevel\": \"warning\" },\n")
            sb.append("  \"inbounds\": [\n")
            sb.append("    {\n")
            sb.append("      \"port\": $inboundPort,\n")
            sb.append("      \"listen\": \"127.0.0.1\",\n")
            sb.append("      \"protocol\": \"socks\",\n")
            sb.append("      \"settings\": { \"udp\": true, \"auth\": \"noauth\" },\n")
            sb.append("      \"sniffing\": { \"enabled\": true, \"destOverride\": [\"http\", \"tls\"] }\n")
            sb.append("    }\n")
            sb.append("  ],\n")
            sb.append("  \"outbounds\": [\n")
            sb.append("    {\n")
            sb.append("      \"protocol\": \"${config.protocol.name.toLowerCase()}\",\n")
            sb.append("      \"settings\": {\n")
            when (config.protocol) {
                XrayProtocol.VMESS -> {
                    sb.append("        \"vnext\": [{\n")
                    sb.append("          \"address\": \"${config.host}\",\n")
                    sb.append("          \"port\": ${config.port},\n")
                    sb.append("          \"users\": [{\n")
                    sb.append("            \"id\": \"${config.uuid}\",\n")
                    sb.append("            \"alterId\": ${config.alterId},\n")
                    sb.append("            \"security\": \"auto\"\n")
                    sb.append("          }]\n")
                    sb.append("        }]")
                }
                XrayProtocol.VLESS -> {
                    sb.append("        \"vnext\": [{\n")
                    sb.append("          \"address\": \"${config.host}\",\n")
                    sb.append("          \"port\": ${config.port},\n")
                    sb.append("          \"users\": [{\n")
                    sb.append("            \"id\": \"${config.uuid}\",\n")
                    sb.append("            \"flow\": \"${config.flow}\",\n")
                    sb.append("            \"encryption\": \"none\"\n")
                    sb.append("          }]\n")
                    sb.append("        }]")
                }
                XrayProtocol.TROJAN -> {
                    sb.append("        \"servers\": [{\n")
                    sb.append("          \"address\": \"${config.host}\",\n")
                    sb.append("          \"port\": ${config.port},\n")
                    sb.append("          \"password\": \"${config.uuid}\"\n")
                    sb.append("        }]")
                }
            }
            sb.append("      },\n")
            sb.append("      \"streamSettings\": ${buildStreamSettings(config)},\n")
            if (config.muxEnabled) {
                sb.append("      \"mux\": { \"enabled\": true, \"concurrency\": ${config.muxConcurrency} },\n")
            }
            sb.append("      \"tag\": \"proxy\"\n")
            sb.append("    }\n")
            sb.append("  ],\n")
            sb.append("  \"dns\": { \"servers\": [\"1.1.1.1\", \"8.8.8.8\"], \"hosts\": {} }\n")
            sb.append("}")
            return sb.toString()
        }

        private fun buildStreamSettings(config: XrayConfig): String {
            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"network\": \"${config.network.name.toLowerCase()}\",\n")
            sb.append("  \"security\": \"${config.security.name.toLowerCase()}\",\n")
            
            when (config.network) {
                XrayNetwork.WS -> {
                    sb.append("  \"wsSettings\": {\n")
                    sb.append("    \"path\": \"${config.path}\",\n")
                    if (config.hostHeader.isNotBlank()) {
                        sb.append("    \"headers\": { \"Host\": \"${config.hostHeader}\" }\n")
                    }
                    sb.append("  },\n")
                }
                XrayNetwork.GRPC -> {
                    sb.append("  \"grpcSettings\": {\n")
                    sb.append("    \"serviceName\": \"${config.grpcServiceName}\"\n")
                    sb.append("  },\n")
                }
                XrayNetwork.H2 -> {
                    sb.append("  \"httpSettings\": {\n")
                    sb.append("    \"path\": \"${config.path}\",\n")
                    if (config.hostHeader.isNotBlank()) {
                        sb.append("    \"headers\": { \"Host\": \"${config.hostHeader}\" }\n")
                    }
                    sb.append("  },\n")
                }
                XrayNetwork.QUIC -> {
                    sb.append("  \"quicSettings\": {\n")
                    sb.append("    \"security\": \"none\",\n")
                    sb.append("    \"key\": \"\",\n")
                    sb.append("    \"header\": { \"type\": \"none\" }\n")
                    sb.append("  },\n")
                }
            }

            when (config.security) {
                XraySecurity.TLS -> {
                    sb.append("  \"tlsSettings\": {\n")
                    sb.append("    \"serverName\": \"${config.sni}\",\n")
                    if (config.fingerprint.isNotBlank()) {
                        sb.append("    \"fingerprint\": \"${config.fingerprint}\",\n")
                    }
                    sb.append("    \"allowInsecure\": ${config.allowInsecure.toString().toLowerCase()}\n")
                    sb.append("  }")
                }
                XraySecurity.REALITY -> {
                    sb.append("  \"realitySettings\": {\n")
                    sb.append("    \"serverName\": \"${config.sni}\",\n")
                    sb.append("    \"publicKey\": \"${config.realityPublicKey}\",\n")
                    sb.append("    \"shortId\": \"${config.realityShortId}\",\n")
                    sb.append("    \"spiderX\": \"${config.realitySpiderX}\",\n")
                    if (config.fingerprint.isNotBlank()) {
                        sb.append("    \"fingerprint\": \"${config.fingerprint}\"\n")
                    }
                    sb.append("  }")
                }
                else -> {
                    sb.append("  \"tlsSettings\": {}")
                }
            }
            
            sb.append("\n}")
            return sb.toString()
        }
    }
}
