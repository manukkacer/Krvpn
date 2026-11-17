package com.krvpn.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krvpn.app.data.local.ConfigRepository
import com.krvpn.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState = _uiState.asStateFlow()

    // Actions
    fun createSshConfig(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        payload: String = "",
        sni: String = "",
        payloadTemplateId: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val ssh = SshConfig(
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    payload = payload,
                    sni = sni,
                    payloadTemplateId = payloadTemplateId
                )
                val config = KrConfig(
                    name = name,
                    type = ConfigType.SSH,
                    ssh = ssh
                )
                configRepository.save(config)
                _uiState.update { it.copy(isSaving = false, savedConfig = config) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun createXrayConfig(
        name: String,
        protocol: XrayProtocol,
        host: String,
        port: Int,
        uuid: String,
        security: XraySecurity = XraySecurity.TLS,
        network: XrayNetwork = XrayNetwork.TCP,
        sni: String = "",
        fingerprint: String = "",
        path: String = "",
        hostHeader: String = "",
        flow: String = "",
        realityPublicKey: String = "",
        realityShortId: String = "",
        realitySpiderX: String = "",
        allowInsecure: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val xray = XrayConfig(
                    protocol = protocol,
                    host = host,
                    port = port,
                    uuid = uuid,
                    security = security,
                    network = network,
                    sni = sni,
                    fingerprint = fingerprint,
                    path = path,
                    hostHeader = hostHeader,
                    flow = flow,
                    realityPublicKey = realityPublicKey,
                    realityShortId = realityShortId,
                    realitySpiderX = realitySpiderX,
                    allowInsecure = allowInsecure
                )
                val config = KrConfig(
                    name = name,
                    type = when (protocol) {
                        XrayProtocol.VMESS -> ConfigType.VMESS
                        XrayProtocol.VLESS -> ConfigType.VLESS
                        XrayProtocol.TROJAN -> ConfigType.TROJAN
                        else -> ConfigType.VMESS
                    },
                    xray = xray
                )
                configRepository.save(config)
                _uiState.update { it.copy(isSaving = false, savedConfig = config) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun createSlowDnsConfig(
        name: String,
        resolver: String,
        nameserver: String,
        publicKey: String,
        recordType: SlowDnsRecordType = SlowDnsRecordType.AUTO
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val slowDns = SlowDnsConfig(
                    resolver = resolver,
                    nameserver = nameserver,
                    publicKey = publicKey,
                    recordType = recordType
                )
                val config = KrConfig(
                    name = name,
                    type = ConfigType.SLOWDNS,
                    slowDns = slowDns
                )
                configRepository.save(config)
                _uiState.update { it.copy(isSaving = false, savedConfig = config) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun importFromLink(link: String, password: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            try {
                val config = configRepository.parseConfigLink(link) ?: throw Exception("Invalid link format")
                val saved = configRepository.importConfig(configRepository.generateLink(config).also { configRepository.save(config) }, password)
                _uiState.update { it.copy(isImporting = false, importedConfig = saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = e.message) }
            }
        }
    }

    fun exportConfig(configId: String, password: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            try {
                val exported = configRepository.exportConfig(configId, password)
                _uiState.update { it.copy(isExporting = false, exportedData = exported) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun generateShareLink(config: KrConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            try {
                val link = configRepository.generateLink(config)
                _uiState.update { it.copy(isExporting = false, exportedData = link) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun updateConfig(config: KrConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                configRepository.save(config.copyWithUpdatedTimestamp())
                _uiState.update { it.copy(isSaving = false, savedConfig = config) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteConfig(configId: String) {
        viewModelScope.launch {
            configRepository.delete(configId)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(savedConfig = null, importedConfig = null, exportedData = null, error = null) }
    }

    data class ConfigUiState(
        val isSaving: Boolean = false,
        val isImporting: Boolean = false,
        val isExporting: Boolean = false,
        val savedConfig: KrConfig? = null,
        val importedConfig: KrConfig? = null,
        val exportedData: String? = null,
        val error: String? = null
    )
}
