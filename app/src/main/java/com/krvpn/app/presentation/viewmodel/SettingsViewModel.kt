package com.krvpn.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krvpn.app.data.local.SettingsRepository
import com.krvpn.app.data.model.KrSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Settings flows
    val themeMode = settingsRepository.themeMode
    val localeTag = settingsRepository.localeTag
    val autoReconnect = settingsRepository.autoReconnect
    val batteryOptimizationRequested = settingsRepository.batteryOptimizationRequested
    val notificationsEnabled = settingsRepository.notificationsEnabled
    val logLevel = settingsRepository.logLevel
    val maxLogEntries = settingsRepository.maxLogEntries
    val enablePacketCapture = settingsRepository.enablePacketCapture
    val dnsOverHttps = settingsRepository.dnsOverHttps
    val customDohUrl = settingsRepository.customDohUrl
    val preferIpv6 = settingsRepository.preferIpv6
    val mtu = settingsRepository.mtu
    val tcpFastOpen = settingsRepository.tcpFastOpen
    val udpEnabled = settingsRepository.udpEnabled

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    // Actions
    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setLocaleTag(tag: String) {
        viewModelScope.launch {
            settingsRepository.setLocaleTag(tag)
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnect(enabled)
        }
    }

    fun setBatteryOptimizationRequested(requested: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBatteryOptimizationRequested(requested)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setLogLevel(level: Int) {
        viewModelScope.launch {
            settingsRepository.setLogLevel(level)
        }
    }

    fun setMaxLogEntries(max: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxLogEntries(max)
        }
    }

    fun setEnablePacketCapture(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnablePacketCapture(enabled)
        }
    }

    fun setDnsOverHttps(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDnsOverHttps(enabled)
        }
    }

    fun setCustomDohUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setCustomDohUrl(url)
        }
    }

    fun setPreferIpv6(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPreferIpv6(enabled)
        }
    }

    fun setMtu(mtuValue: Int) {
        viewModelScope.launch {
            settingsRepository.setMtu(mtuValue)
        }
    }

    fun setTcpFastOpen(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTcpFastOpen(enabled)
        }
    }

    fun setUdpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUdpEnabled(enabled)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _uiState.update { SettingsUiState() }
        }
    }

    data class SettingsUiState(
        val themeMode: Int = 0,
        val localeTag: String = "",
        val autoReconnect: Boolean = true,
        val batteryOptimizationRequested: Boolean = false,
        val notificationsEnabled: Boolean = true,
        val logLevel: Int = 2,
        val maxLogEntries: Int = 5000,
        val enablePacketCapture: Boolean = false,
        val dnsOverHttps: Boolean = false,
        val customDohUrl: String = "",
        val preferIpv6: Boolean = false,
        val mtu: Int = 1500,
        val tcpFastOpen: Boolean = false,
        val udpEnabled: Boolean = true
    )
}
