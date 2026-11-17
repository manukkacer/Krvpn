package com.krvpn.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krvpn.app.domain.tunnel.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val tunnelController: TunnelController
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState = _uiState.asStateFlow()

    fun runDiagnostics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            
            val internet = checkInternetAccess()
            val dns = checkDnsResolution()
            val tunnelPath = checkTunnelPath()
            val battery = checkBatteryOptimization()
            
            _uiState.update { it.copy(
                isRunning = false,
                internetAccess = internet,
                dnsResolution = dns,
                tunnelPath = tunnelPath,
                batteryHandling = battery,
                lastRun = System.currentTimeMillis()
            )
        }
    }

    private suspend fun checkInternetAccess(): DiagnosticsResult = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val address = InetAddress.getByName("connectivitycheck.gstatic.com")
            val reachable = address.isReachable(5000)
            val latency = System.currentTimeMillis() - start
            
            if (reachable) {
                DiagnosticsResult.Success("Internet accessible (${latency}ms)")
            } else {
                DiagnosticsResult.Warning("Internet check timeout")
            }
        } catch (e: Exception) {
            DiagnosticsResult.Error("Internet check failed: ${e.message}")
        }
    }

    private suspend fun checkDnsResolution(): DiagnosticsResult = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val addresses = InetAddress.getAllByName("cloudflare.com")
            val latency = System.currentTimeMillis() - start
            
            if (addresses.isNotEmpty()) {
                DiagnosticsResult.Success("DNS working (${latency}ms) - ${addresses.first().hostAddress}")
            } else {
                DiagnosticsResult.Error("DNS returned no addresses")
            }
        } catch (e: Exception) {
            DiagnosticsResult.Error("DNS check failed: ${e.message}")
        }
    }

    private suspend fun checkTunnelPath(): DiagnosticsResult = withContext(Dispatchers.IO) {
        val state = tunnelController.state.value
        return@withContext when (state) {
            is TunnelController.TunnelState.CONNECTED -> {
                try {
                    // Try to reach a known endpoint through tunnel
                    val address = InetAddress.getByName("1.1.1.1")
                    val reachable = address.isReachable(3000)
                    if (reachable) {
                        DiagnosticsResult.Success("Tunnel path active")
                    } else {
                        DiagnosticsResult.Warning("Tunnel connected but path test failed")
                    }
                } catch (e: Exception) {
                    DiagnosticsResult.Error("Tunnel path check failed: ${e.message}")
                }
            }
            is TunnelController.TunnelState.CONNECTING -> DiagnosticsResult.Info("Tunnel connecting…")
            is TunnelController.TunnelState.HELD -> DiagnosticsResult.Info("Tunnel held: ${state.reason}")
            else -> DiagnosticsResult.Info("Tunnel not connected")
        }
    }

    private suspend fun checkBatteryOptimization(): DiagnosticsResult = withContext(Dispatchers.IO) {
        // Check if app is exempt from battery optimization
        // This would need PowerManager API
        DiagnosticsResult.Info("Battery optimization check requires PowerManager")
    }

    sealed interface DiagnosticsResult {
        data class Success(val message: String) : DiagnosticsResult
        data class Warning(val message: String) : DiagnosticsResult
        data class Error(val message: String) : DiagnosticsResult
        data class Info(val message: String) : DiagnosticsResult
    }

    data class DiagnosticsUiState(
        val isRunning: Boolean = false,
        val internetAccess: DiagnosticsResult? = null,
        val dnsResolution: DiagnosticsResult? = null,
        val tunnelPath: DiagnosticsResult? = null,
        val batteryHandling: DiagnosticsResult? = null,
        val lastRun: Long = 0
    )
}
