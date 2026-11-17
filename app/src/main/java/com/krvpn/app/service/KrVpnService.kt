package com.krvpn.app.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import com.krvpn.app.domain.tunnel.TunnelController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * KRVPN Core VPN Service
 * Extends Android VpnService to manage TUN interface
 * Delegates tunnel logic to TunnelController
 */
@AndroidEntryPoint
class KrVpnService : VpnService() {

    private const val TAG = "KrVpnService"

    // Actions
    companion object {
        const val ACTION_START = "com.krvpn.app.action.VPN_START"
        const val ACTION_STOP = "com.krvpn.app.action.VPN_STOP"
        const val ACTION_HOLD = "com.krvpn.app.action.VPN_HOLD"
        const val ACTION_RESUME = "com.krvpn.app.action.VPN_RESUME"
        const val ACTION_PREPARE = "com.krvpn.app.action.VPN_PREPARE"
        const val ACTION_RECONNECT = "com.krvpn.app.action.VPN_RECONNECT"
        
        const val EXTRA_CONFIG_ID = "extra_config_id"
        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_HOLD_REASON = "extra_hold_reason"
    }

    private var tunnelController: TunnelController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KrVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        
        scope.launch {
            when (action) {
                ACTION_START -> handleStart(intent)
                ACTION_STOP -> handleStop()
                ACTION_HOLD -> handleHold(intent)
                ACTION_RESUME -> handleResume()
                ACTION_PREPARE -> handlePrepare()
                ACTION_RECONNECT -> tunnelController?.triggerReconnect()
            }
        }
        
        return START_STICKY
    }

    private suspend fun handleStart(intent: Intent) {
        val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
            ?: return Log.e(TAG, "No config ID provided")
        
        val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "KRVPN Tunnel"
        
        // Get config from repository (via Hilt injection)
        // For now, we'll need to get it from the application
        val app = application as com.krvpn.app.KrApplication
        val config = app.configRepository.getById(configId).await()
            ?: return Log.e(TAG, "Config not found: $configId")
        
        if (tunnelController == null) {
            tunnelController = TunnelController(
                context = this,
                vpnService = this,
                configRepository = app.configRepository,
                settingsRepository = app.settingsRepository,
                security = app.securityManager
            )
        }
        
        val success = tunnelController?.start(config) ?: false
        if (!success) {
            stopSelf()
        }
    }

    private suspend fun handleStop() {
        await tunnelController?.stop()
        stopSelf()
    }

    private suspend fun handleHold(intent: Intent) {
        val reason = intent.getStringExtra(EXTRA_HOLD_REASON) ?: "User hold"
        await tunnelController?.hold(reason)
    }

    private suspend fun handleResume() {
        await tunnelController?.resume()
    }

    private suspend fun handlePrepare() {
        // Prepare VPN permission
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Need user permission - send broadcast or start activity
            Log.i(TAG, "VPN permission required")
        } else {
            Log.i(TAG, "VPN already prepared")
        }
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.w(TAG, "VPN permission revoked")
        scope.launch { tunnelController?.stop() }
        
        // Notify foreground service
        val intent = Intent(this, KrForegroundService::class.java)
            .setAction("com.krvpn.app.action.VPN_REVOKED")
        startService(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        scope.launch { tunnelController?.stop() }
        tunnelController = null
        Log.i(TAG, "KrVpnService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Helper to expose protect() for socket protection
    fun protectSocket(socket: java.net.Socket): Boolean = protect(socket)
    fun protectSocket(socket: java.net.DatagramSocket): Boolean = protect(socket)
}
