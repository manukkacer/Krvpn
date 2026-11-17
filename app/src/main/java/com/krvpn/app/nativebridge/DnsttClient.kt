package com.krvpn.app.nativebridge

import android.content.Context
import android.util.Log
import com.krvpn.app.data.model.SlowDnsConfig
import com.krvpn.app.data.model.SlowDnsRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNSTT (SlowDNS) Client Native Bridge
 * Wraps libdnstt.so for DNS tunneling via TXT/NULL records
 */
class DnsttClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DnsttClient"
        private const val LIB_NAME = "dnstt"
        @Volatile private var instance: DnsttClient? = null
        private val initialized = AtomicBoolean(false)

        fun getInstance(context: Context): DnsttClient {
            return instance ?: synchronized(this) {
                instance ?: DnsttClient(context.applicationContext).also { instance = it }
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
            Log.i(TAG, "libdnstt.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libdnstt.so", e)
            initialized.set(false)
        }
    }

    // ============ NATIVE METHODS ============

    /**
     * Start DNSTT client
     * @param resolver DNS resolver IP or DoH/DoT URL
     * @param nameserver Authoritative nameserver for domain
     * @param publicKey Base64 encoded public key
     * @param recordType Record type (TXT, NULL, etc.)
     * @param localPort Local SOCKS5 port to bind
     * @return true if started successfully
     */
    external fun nativeStart(
        resolver: String,
        nameserver: String,
        publicKey: String,
        recordType: Int,
        localPort: Int
    ): Boolean

    /**
     * Stop DNSTT client
     */
    external fun nativeStop(): Boolean

    /**
     * Check if DNSTT is running
     */
    external fun nativeIsRunning(): Boolean

    /**
     * Get statistics
     * @return long array [queriesSent, responsesReceived, bytesUp, bytesDown]
     */
    external fun nativeGetStats(): LongArray

    // ============ KOTLIN WRAPPER METHODS ============

    suspend fun start(config: SlowDnsConfig, localPort: Int = 1080): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        val recordTypeInt = when (config.recordType) {
            SlowDnsRecordType.AUTO -> 0
            SlowDnsRecordType.TXT -> 1
            SlowDnsRecordType.NULL -> 2
            SlowDnsRecordType.NS -> 3
            SlowDnsRecordType.CNAME -> 4
            SlowDnsRecordType.MX -> 5
            SlowDnsRecordType.SRV -> 6
            SlowDnsRecordType.A -> 7
            SlowDnsRecordType.AAAA -> 8
        }
        nativeStart(
            resolver = config.resolver,
            nameserver = config.nameserver,
            publicKey = config.publicKey,
            recordType = recordTypeInt,
            localPort = localPort
        )
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext true
        nativeStop()
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        initialized.get() && nativeIsRunning()
    }

    suspend fun getStats(): LongArray = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext longArrayOf(0L, 0L, 0L, 0L)
        nativeGetStats()
    }

    /**
     * Test DNSTT connection
     */
    suspend fun testConnection(config: SlowDnsConfig): Boolean = withContext(Dispatchers.IO) {
        // Start with a test port, then stop
        val testPort = (10000..20000).random()
        val started = start(config, testPort)
        if (started) {
            Thread.sleep(2000) // Wait for connection
            val running = isRunning()
            stop()
            return@withContext running
        }
        false
    }
}
