package com.krvpn.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krvpn.app.domain.tunnel.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val tunnelController: TunnelController
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeStats()
    }

    private fun observeStats() {
        viewModelScope.launch {
            combine(
                tunnelController.stats,
                tunnelController.state,
                tunnelController.currentConfig
            ) { stats, state, config ->
                _uiState.update { it.copy(
                    stats = stats,
                    tunnelState = state,
                    currentConfig = config,
                    isConnected = state is TunnelController.TunnelState.CONNECTED
                ) }
            }.collect()
        }
    }

    fun resetStats() {
        // Reset would require TunnelController to expose a reset method
        // For now, stats reset on reconnect
    }

    data class StatisticsUiState(
        val stats: TunnelController.TunnelStats? = null,
        val tunnelState: TunnelController.TunnelState = TunnelController.TunnelState.DISCONNECTED,
        val currentConfig: com.krvpn.app.data.model.KrConfig? = null,
        val isConnected: Boolean = false
    ) {
        val formattedUpload: String
            get() = formatBytes(stats?.txBytes ?: 0)
        val formattedDownload: String
            get() = formatBytes(stats?.rxBytes ?: 0)
        val formattedTotal: String
            get() = formatBytes((stats?.txBytes ?: 0) + (stats?.rxBytes ?: 0))
        val uploadSpeed: String
            get() = formatSpeed(stats?.txSpeed ?: 0.0)
        val downloadSpeed: String
            get() = formatSpeed(stats?.rxSpeed ?: 0.0)
        val duration: String
            get() = formatDuration(stats?.durationMs ?: 0)

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }

        private fun formatSpeed(bytesPerSec: Double): String {
            return when {
                bytesPerSec >= 1_000_000 -> String.format("%.2f MB/s", bytesPerSec / 1_000_000)
                bytesPerSec >= 1_000 -> String.format("%.2f KB/s", bytesPerSec / 1_000)
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
    }
}
