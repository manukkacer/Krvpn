package com.krvpn.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.krvpn.app.R
import com.krvpn.app.domain.tunnel.TunnelController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * KRVPN Foreground Service
 * Shows persistent notification with connection status
 * Handles quick settings tile integration
 */
@AndroidEntryPoint
class KrForegroundService : Service(), LifecycleObserver {

    private const val TAG = "KrForegroundService"
    private const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "krvpn_connection"
    private const val CHANNEL_NAME = "KRVPN Connection"
    private const val CHANNEL_DESC = "Manages VPN connection status and tunnel"

    private var notificationManager: NotificationManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Actions
    companion object {
        const val ACTION_CONNECT = "com.krvpn.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.krvpn.app.action.DISCONNECT"
        const val ACTION_HOLD = "com.krvpn.app.action.HOLD"
        const val ACTION_OPEN_APP = "com.krvpn.app.action.OPEN_APP"
        const val ACTION_VPN_REVOKED = "com.krvpn.app.action.VPN_REVOKED"
        const val ACTION_SYSTEM_START = "com.krvpn.app.action.SYSTEM_START"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_CONNECT -> {
                // Start VPN via KrVpnService
                val vpnIntent = Intent(this, KrVpnService::class.java)
                    .setAction(KrVpnService.ACTION_START)
                    .putExtra(KrVpnService.EXTRA_CONFIG_ID, intent.getStringExtra(KrVpnService.EXTRA_CONFIG_ID))
                    .putExtra(KrVpnService.EXTRA_SESSION_NAME, intent.getStringExtra(KrVpnService.EXTRA_SESSION_NAME))
                startService(vpnIntent)
            }
            ACTION_DISCONNECT -> {
                val vpnIntent = Intent(this, KrVpnService::class.java)
                    .setAction(KrVpnService.ACTION_STOP)
                startService(vpnIntent)
            }
            ACTION_HOLD -> {
                val vpnIntent = Intent(this, KrVpnService::class.java)
                    .setAction(KrVpnService.ACTION_HOLD)
                    .putExtra(KrVpnService.EXTRA_HOLD_REASON, intent.getStringExtra(KrVpnService.EXTRA_HOLD_REASON))
                startService(vpnIntent)
            }
            ACTION_VPN_REVOKED -> {
                updateNotification(TunnelController.TunnelState.DISCONNECTED, null)
            }
            ACTION_SYSTEM_START -> {
                // Auto-start on boot if enabled
                handleSystemStart()
            }
            ACTION_OPEN_APP -> {
                openMainActivity()
            }
        }
        
        return START_STICKY
    }

    private fun handleSystemStart() {
        // Check if auto-start is enabled and last config exists
        scope.launch {
            val app = application as com.krvpn.app.KrApplication
            val autoStart = app.settingsRepository.getAutoReconnect()
            val lastConfigId = app.settingsRepository.getLastConnectedConfigId()
            
            if (autoStart && lastConfigId.isNotBlank()) {
                val config = app.configRepository.getById(lastConfigId).await()
                if (config != null && !config.isExpired) {
                    val intent = Intent(this@KrForegroundService, KrVpnService::class.java)
                        .setAction(KrVpnService.ACTION_START)
                        .putExtra(KrVpnService.EXTRA_CONFIG_ID, config.id)
                    startService(intent)
                }
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, com.krvpn.app.presentation.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Update notification with current tunnel state
     */
    fun updateNotification(state: TunnelController.TunnelState, stats: TunnelController.TunnelStats?) {
        val notification = buildNotification(state, stats)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: TunnelController.TunnelState, stats: TunnelController.TunnelStats?): Notification {
        val (title, text, icon, color, progress, ongoing) = when (state) {
            is TunnelController.TunnelState.CONNECTING -> (
                "Connecting…",
                "Establishing secure tunnel…",
                R.drawable.ic_vpn_connecting,
                Color.parseColor("#FFB300"),
                0,
                true
            )
            is TunnelController.TunnelState.CONNECTED -> {
                val statsText = stats?.let { formatStats(it) } ?: "Connected"
                (
                    "KRVPN Connected",
                    statsText,
                    R.drawable.ic_vpn_connected,
                    Color.parseColor("#2E7D32"),
                    100,
                    true
                )
            }
            is TunnelController.TunnelState.HELD -> (
                "KRVPN Paused",
                "Tunnel held: ${state.reason}",
                R.drawable.ic_vpn_held,
                Color.parseColor("#FFB300"),
                0,
                true
            )
            is TunnelController.TunnelState.RECONNECTING -> (
                "Reconnecting… (${state.attempt}/5)",
                "Attempting to restore connection…",
                R.drawable.ic_vpn_reconnecting,
                Color.parseColor("#FFB300"),
                0,
                true
            )
            is TunnelController.TunnelState.ERROR -> (
                "Connection Error",
                state.message,
                R.drawable.ic_vpn_error,
                Color.parseColor("#C62828"),
                0,
                false
            )
            else -> (
                "KRVPN Disconnected",
                "Tap to connect",
                R.drawable.ic_vpn_disconnected,
                Color.parseColor("#757575"),
                0,
                false
            )
        }

        val openAppIntent = Intent(this, com.krvpn.app.presentation.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, KrForegroundService::class.java)
            .setAction(ACTION_DISCONNECT)
        val disconnectPending = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val holdIntent = Intent(this, KrForegroundService::class.java)
            .setAction(ACTION_HOLD)
            .putExtra(KrVpnService.EXTRA_HOLD_REASON, "User hold")
        val holdPending = PendingIntent.getService(
            this, 2, holdIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPending)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(if (ongoing) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)

        // Add actions based on state
        when (state) {
            is TunnelController.TunnelState.CONNECTED, is TunnelController.TunnelState.CONNECTING -> {
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_pause,
                        "Hold",
                        holdPending
                    ).build()
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_stop,
                        "Disconnect",
                        disconnectPending
                    ).build()
                )
            }
            is TunnelController.TunnelState.HELD -> {
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_play,
                        "Resume",
                        PendingIntent.getService(
                            this, 3,
                            Intent(this, KrVpnService::class.java).setAction(KrVpnService.ACTION_RESUME),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    ).build()
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_stop,
                        "Disconnect",
                        disconnectPending
                    ).build()
                )
            }
            else -> {
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_play,
                        "Connect",
                        PendingIntent.getActivity(
                            this, 4,
                            Intent(this, com.krvpn.app.presentation.ui.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    ).build()
                )
            }
        }

        // Progress for connecting
        if (state is TunnelController.TunnelState.CONNECTING) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun formatStats(stats: TunnelController.TunnelStats): String {
        val up = formatSpeed(stats.txSpeed)
        val down = formatSpeed(stats.rxSpeed)
        val duration = formatDuration(stats.durationMs)
        return "↑ $up  ↓ $down  •  $duration"
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSec / 1_000_000)
            bytesPerSec >= 1_000 -> String.format("%.1f KB/s", bytesPerSec / 1_000)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatDuration(ms: Long): String {
        val secs = ms / 1000
        val hours = secs / 3600
        val mins = (secs % 3600) / 60
        val secsRem = secs % 60
        return if (hours > 0) "%02d:%02d:%02d".format(hours, mins, secsRem)
        else "%02d:%02d".format(mins, secsRem)
    }

    override fun onDestroy() {
        scope.cancel()
        notificationManager?.cancel(NOTIFICATION_ID)
        Log.i(TAG, "Foreground service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        // Service started
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        // Service stopped
    }
}

/**
 * Quick Settings Tile Service for KRVPN
 * Add to Android Quick Settings panel
 */
class KrVpnTileService : TileService() {

    private const val TAG = "KrVpnTile"

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        toggleVpn()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val app = application as com.krvpn.app.KrApplication
        
        // Check current tunnel state
        // This would need to connect to TunnelController state
        // For now, show inactive state
        tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
        tile.label = "KRVPN"
        tile.subtitle = "Tap to connect"
        tile.updateTile()
    }

    private fun toggleVpn() {
        val tile = qsTile ?: return
        
        if (tile.state == android.service.quicksettings.Tile.STATE_ACTIVE) {
            // Disconnect
            val intent = Intent(this, KrForegroundService::class.java)
                .setAction(KrForegroundService.ACTION_DISCONNECT)
            startService(intent)
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
        } else {
            // Connect - open app to select config
            val intent = Intent(this, com.krvpn.app.presentation.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
            tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }
}
