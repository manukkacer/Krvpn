package com.krvpn.app.nativebridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HEV SOCKS5 Tunnel Native Bridge
 * Wraps libhev-socks5-tunnel.so for TUN ↔ SOCKS5 proxy
 * This is the core component that connects Android VpnService TUN interface to SOCKS5 proxy
 */
class HevSocks5Tunnel private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HevSocks5Tunnel"
        private const val LIB_NAME = "hev-socks5-tunnel"
        @Volatile private var instance: HevSocks5Tunnel? = null
        private val initialized = AtomicBoolean(false)

        fun getInstance(context: Context): HevSocks5Tunnel {
            return instance ?: synchronized(this) {
                instance ?: HevSocks5Tunnel(context.applicationContext).also { instance = it }
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
            Log.i(TAG, "libhev-socks5-tunnel.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libhev-socks5-tunnel.so", e)
            initialized.set(false)
        }
    }

    // ============ NATIVE METHODS ============

    /**
     * Start SOCKS5 tunnel service
     * @param socksHost SOCKS5 proxy host (e.g., "127.0.0.1")
     * @param socksPort SOCKS5 proxy port (e.g., 1080)
     * @return true if started successfully
     */
    external fun nativeStartService(socksHost: String, socksPort: Int): Boolean

    /**
     * Stop SOCKS5 tunnel service
     */
    external fun nativeStopService(): Boolean

    /**
     * Check if tunnel is running
     */
    external fun nativeIsRunning(): Boolean

    /**
     * Get traffic statistics
     * @return long array [txBytes, rxBytes]
     */
    external fun nativeGetStats(): LongArray

    /**
     * Set UDP relay mode
     * @param enabled true to enable UDP relay
     */
    external fun nativeSetUdpRelay(enabled: Boolean): Boolean

    /**
     * Set TCP MSS (Maximum Segment Size)
     * @param mss MSS value (typically 1380-1460 for TUN)
     */
    external fun nativeSetTcpMss(mss: Int): Boolean

    // ============ KOTLIN WRAPPER METHODS ============

    suspend fun start(socksHost: String, socksPort: Int): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        nativeStartService(socksHost, socksPort)
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext true
        nativeStopService()
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        initialized.get() && nativeIsRunning()
    }

    suspend fun getStats(): LongArray = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext longArrayOf(0L, 0L)
        nativeGetStats()
    }

    suspend fun setUdpRelay(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        nativeSetUdpRelay(enabled)
    }

    suspend fun setTcpMss(mss: Int): Boolean = withContext(Dispatchers.IO) {
        if (!initialized.get()) return@withContext false
        nativeSetTcpMss(mss)
    }

    /**
     * Check if library is available
     */
    fun isAvailable(): Boolean = initialized.get()
}
