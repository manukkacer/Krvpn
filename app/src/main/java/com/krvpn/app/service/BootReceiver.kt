package com.krvpn.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot Receiver - Auto-start VPN on device boot
 * Handles BOOT_COMPLETED and QUICKBOOT_POWERON
 */
class BootReceiver : BroadcastReceiver() {

    private const val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received broadcast: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_QUICKBOOT_POWERON,
            "android.intent.action.LOCKED_BOOT_COMPLETED" -> {
                // Start foreground service which will handle auto-start logic
                val serviceIntent = Intent(context, KrForegroundService::class.java)
                    .setAction(KrForegroundService.ACTION_SYSTEM_START)
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                // Network changed - trigger reconnect if VPN is active
                val vpnIntent = Intent(context, KrVpnService::class.java)
                    .setAction(KrVpnService.ACTION_RECONNECT)
                context.startService(vpnIntent)
            }
        }
    }
}
