package com.krvpn.app.nativebridge

import android.content.Context
import android.util.Log
import com.jcraft.jsch.*
import com.krvpn.app.data.model.SshConfig
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SSH Tunnel Implementation (Pure Kotlin/Java using JSch)
 * Provides SSH tunneling with dynamic port forwarding (SOCKS5)
 * This doesn't need a native library - uses JSch Java library
 */
class SshTunnel(private val context: Context) {

    companion object {
        private const val TAG = "SshTunnel"
        private var instance: SshTunnel? = null

        fun getInstance(context: Context): SshTunnel {
            return instance ?: synchronized(this) {
                instance ?: SshTunnel(context.applicationContext).also { instance = it }
            }
        }
    }

    // State
    private var session: Session? = null
    private var socksServerSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val socksPort = AtomicInteger(0)
    private val txBytes = AtomicLong(0)
    private val rxBytes = AtomicLong(0)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // Configuration
    private var currentConfig: SshConfig? = null
    private var currentPayload: String = ""

    /**
     * Start SSH tunnel with dynamic SOCKS5 proxy
     */
    suspend fun start(config: SshConfig, localPort: Int = 0): Boolean {
        return withContext(Dispatchers.IO) {
            if (isRunning.get()) {
                stop()
            }

            currentConfig = config
            currentPayload = buildPayload(config)

            try {
                val jsch = JSch()
                session = jsch.getSession(config.username, config.host, config.port)
                session.setPassword(config.password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session.setTimeout(config.connectTimeout)
                session.connect()

                // Setup dynamic port forwarding (SOCKS5)
                val boundPort = session.setPortForwardingD(localPort)
                socksPort.set(boundPort)

                // Start keep-alive
                startKeepAlive()

                isRunning.set(true)
                txBytes.set(0)
                rxBytes.set(0)

                Log.i(TAG, "SSH tunnel started on 127.0.0.1:$boundPort")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SSH tunnel", e)
                cleanup()
                false
            }
        }
    }

    /**
     * Stop SSH tunnel
     */
    suspend fun stop(): Boolean {
        return withContext(Dispatchers.IO) {
            cleanup()
            true
        }
    }

    private fun cleanup() {
        isRunning.set(false)
        job.cancel()

        try {
            session?.delPortForwardingD(socksPort.get())
        } catch (e: Exception) {
            Log.w(TAG, "Error removing port forwarding", e)
        }

        try {
            session?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting session", e)
        }

        session = null
        socksPort.set(0)
    }

    private fun startKeepAlive() {
        scope.launch {
            val config = currentConfig ?: return@launch
            while (isRunning.get() && !job.isCancelled) {
                try {
                    delay(config.keepAliveInterval * 1000L)
                    if (isRunning.get()) {
                        // Send keep-alive packet
                        session?.sendIgnore()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Keep-alive failed, attempting reconnect", e)
                    if (isRunning.get()) {
                        reconnect()
                    }
                    break
                }
            }
        }
    }

    private suspend fun reconnect() {
        val config = currentConfig ?: return
        var attempts = 0
        while (attempts < config.reconnectAttempts && isRunning.get()) {
            attempts++
            try {
                delay(config.reconnectDelay.toLong())
                val jsch = JSch()
                session = jsch.getSession(config.username, config.host, config.port)
                session.setPassword(config.password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setTimeout(config.connectTimeout)
                session.connect()
                
                val boundPort = session.setPortForwardingD(socksPort.get())
                socksPort.set(boundPort)
                Log.i(TAG, "SSH reconnected on port $boundPort (attempt $attempts)")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt $attempts failed", e)
            }
        }
        // All attempts failed
        isRunning.set(false)
    }

    /**
     * Build SSH payload for custom headers/injection
     */
    private fun buildPayload(config: SshConfig): String {
        return config.payload.ifBlank { "GET / HTTP/1.1\r\nHost: ${config.sni.ifBlank { config.host }}\r\n\r\n" }
    }

    // ============ STATUS METHODS ============

    fun isRunning(): Boolean = isRunning.get()

    fun getSocksPort(): Int = socksPort.get()

    fun getSocksHost(): String = "127.0.0.1"

    fun getStats(): LongArray = longArrayOf(txBytes.get(), rxBytes.get())

    fun getConfig(): SshConfig? = currentConfig

    fun addTxBytes(bytes: Long) = txBytes.addAndGet(bytes)

    fun addRxBytes(bytes: Long) = rxBytes.addAndGet(bytes)

    // ============ PAYLOAD INJECTION (for custom HTTP/WS payloads) ============

    /**
     * Create a custom SSH connection with payload injection
     * Used for HTTP Injector style payloads
     */
    suspend fun startWithPayloadInjection(
        config: SshConfig,
        payload: String,
        localPort: Int = 0
    ): Boolean {
        currentPayload = payload
        return start(config, localPort)
    }
}

// Helper for atomic integer (API 24+)
private class AtomicInteger(private var value: Int = 0) {
    fun get(): Int = value
    fun set(newValue: Int) { value = newValue }
    fun getAndSet(newValue: Int): Int { val old = value; value = newValue; return old }
    fun compareAndSet(expect: Int, update: Int): Boolean = synchronized(this) {
        if (value == expect) { value = update; true } else false
    }
    fun incrementAndGet(): Int = synchronized(this) { value++ }
    fun decrementAndGet(): Int = synchronized(this) { value-- }
}
