package com.krvpn.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krvpn.app.data.local.ConfigRepository
import com.krvpn.app.data.local.SettingsRepository
import com.krvpn.app.domain.tunnel.TunnelController
import com.krvpn.app.domain.tunnel.TunnelController.TunnelState
import com.krvpn.app.domain.tunnel.TunnelController.TunnelStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val settingsRepository: SettingsRepository,
    private val tunnelController: TunnelController
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    // Expose tunnel state
    val tunnelState = tunnelController.state
    val tunnelStats = tunnelController.stats
    val currentConfig = tunnelController.currentConfig
    val configs = configRepository.configs

    init {
        observeTunnelState()
        loadConfigs()
    }

    private fun observeTunnelState() {
        viewModelScope.launch {
            combine(
                tunnelController.state,
                tunnelController.stats,
                tunnelController.currentConfig
            ) { state, stats, config ->
                _uiState.update { it.copy(
                    tunnelState = state,
                    stats = stats,
                    currentConfig = config
                ) }
            }.collect()
        }
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            configRepository.getAll().collect { configList ->
                _uiState.update { it.copy(configs = configList) }
            }
        }
    }

    // Actions
    fun connect(config: com.krvpn.app.data.model.KrConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }
            val success = tunnelController.start(config)
            _uiState.update { it.copy(isConnecting = false, lastError = if (success) null else "Failed to connect") }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            tunnelController.stop()
        }
    }

    fun hold(reason: String = "User hold") {
        viewModelScope.launch {
            tunnelController.hold(reason)
        }
    }

    fun resume() {
        viewModelScope.launch {
            tunnelController.resume()
        }
    }

    fun deleteConfig(configId: String) {
        viewModelScope.launch {
            configRepository.delete(configId)
        }
    }

    fun refreshConfigs() {
        loadConfigs()
    }

    data class HomeUiState(
        val configs: List<com.krvpn.app.data.model.KrConfig> = emptyList(),
        val tunnelState: TunnelState = TunnelController.TunnelState.DISCONNECTED,
        val stats: TunnelStats? = null,
        val currentConfig: com.krvpn.app.data.model.KrConfig? = null,
        val isConnecting: Boolean = false,
        val lastError: String? = null
    )
}
