package com.krvpn.app.data.local

import android.content.Context
import com.krvpn.app.data.model.*
import com.krvpn.app.data.security.KrSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Config Repository - Manages VPN configurations with encryption
 */
class ConfigRepository(
    private val context: Context,
    private val security: KrSecurity
) {

    private val configsMap = ConcurrentHashMap<String, KrConfig>()
    private val _configs = MutableStateFlow<List<KrConfig>>(emptyList())
    val configs = _configs.asStateFlow().distinctUntilChanged()

    private val prefs = context.getSharedPreferences("krvpn_configs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    init {
        loadConfigs()
    }

    // ============ CRUD OPERATIONS ============

    suspend fun getAll(): List<KrConfig> = withContext(Dispatchers.IO) {
        configsMap.values.toList().sortedByDescending { it.updatedAt }
    }

    suspend fun getById(id: String): KrConfig? = withContext(Dispatchers.IO) {
        configsMap[id]
    }

    suspend fun save(config: KrConfig): KrConfig = withContext(Dispatchers.IO) {
        val updated = config.copyWithUpdatedTimestamp()
        configsMap[updated.id] = updated
        persistConfigs()
        _configs.value = configsMap.values.toList().sortedByDescending { it.updatedAt }
        updated
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val removed = configsMap.remove(id) != null
        if (removed) {
            persistConfigs()
            _configs.value = configsMap.values.toList().sortedByDescending { it.updatedAt }
        }
        removed
    }

    suspend fun deleteAll(): Boolean = withContext(Dispatchers.IO) {
        val wasEmpty = configsMap.isEmpty()
        configsMap.clear()
        if (!wasEmpty) {
            persistConfigs()
            _configs.value = emptyList()
        }
        true
    }

    // ============ IMPORT/EXPORT ============

    /**
     * Import config from JSON string (encrypted or plain)
     */
    suspend fun importConfig(jsonString: String, password: String? = null): KrConfig = withContext(Dispatchers.IO) {
        val decryptedJson = if (isEncrypted(jsonString)) {
            security.decryptConfig(jsonString, password)
        } else {
            jsonString
        }
        
        val config = json.decodeFromString<KrConfig>(decryptedJson)
        val withNewId = config.copy(id = java.util.UUID.randomUUID().toString())
        return save(withNewId)
    }

    /**
     * Import multiple configs (batch)
     */
    suspend fun importConfigs(jsonStrings: List<String>, password: String? = null): List<KrConfig> = withContext(Dispatchers.IO) {
        jsonStrings.map { importConfig(it, password) }
    }

    /**
     * Export config to encrypted JSON
     */
    suspend fun exportConfig(id: String, password: String? = null): String? = withContext(Dispatchers.IO) {
        val config = configsMap[id] ?: return@withContext null
        val jsonString = json.encodeToString(config)
        return if (password != null && password.isNotBlank()) {
            security.encryptConfig(jsonString, password)
        } else {
            jsonString
        }
    }

    /**
     * Export all configs
     */
    suspend fun exportAllConfigs(password: String? = null): List<String> = withContext(Dispatchers.IO) {
        configsMap.values.mapNotNull { exportConfig(it.id, password) }
    }

    // ============ CONFIG PARSING ============

    /**
     * Parse config from various link formats
     * Supports: vmess://, vless://, trojan://, ssh://, krvpn://
     */
    suspend fun parseConfigLink(link: String): KrConfig? = withContext(Dispatchers.IO) {
        return when {
            link.startsWith("vmess://") -> parseVmessLink(link)
            link.startsWith("vless://") -> parseVlessLink(link)
            link.startsWith("trojan://") -> parseTrojanLink(link)
            link.startsWith("ssh://") -> parseSshLink(link)
            link.startsWith("krvpn://") -> parseKrVpnLink(link)
            else -> null
        }
    }

    private fun parseVmessLink(link: String): KrConfig? {
        try {
            val base64 = link.substringAfter("vmess://")
            val jsonStr = String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP), "UTF-8")
            val vmess = json.decodeFromString<VmessLink>(jsonStr)
            
            val xray = XrayConfig(
                protocol = XrayProtocol.VMESS,
                host = vmess.add,
                port = vmess.port.toIntOrNull() ?: 443,
                uuid = vmess.id,
                alterId = vmess.aid.toIntOrNull() ?: 0,
                security = when (vmess.tls) { "tls" -> XraySecurity.TLS; else -> XraySecurity.NONE },
                network = when (vmess.net) { "ws" -> XrayNetwork.WS; "grpc" -> XrayNetwork.GRPC; "h2" -> XrayNetwork.H2; "quic" -> XrayNetwork.QUIC; else -> XrayNetwork.TCP },
                sni = vmess.sni,
                fingerprint = vmess.fp,
                path = vmess.path,
                hostHeader = vmess.host,
                allowInsecure = vmess.allowInsecure == "1"
            )
            return KrConfig(name = vmess.ps.ifBlank { "VMess Config" }, type = ConfigType.VMESS, xray = xray)
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseVlessLink(link: String): KrConfig? {
        try {
            val uri = android.net.Uri.parse(link)
            val uuid = uri.getUserInfo() ?: ""
            val host = uri.host ?: ""
            val port = uri.port.ifNegative(443)
            
            val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) }
            
            val xray = XrayConfig(
                protocol = XrayProtocol.VLESS,
                host = host,
                port = port,
                uuid = uuid,
                security = when (query["security"]) { "tls" -> XraySecurity.TLS; "reality" -> XraySecurity.REALITY; else -> XraySecurity.NONE },
                network = when (query["type"]) { "ws" -> XrayNetwork.WS; "grpc" -> XrayNetwork.GRPC; "h2" -> XrayNetwork.H2; "quic" -> XrayNetwork.QUIC; else -> XrayNetwork.TCP },
                sni = query["sni"] ?: "",
                fingerprint = query["fp"] ?: "",
                path = query["path"] ?: "",
                hostHeader = query["host"] ?: "",
                flow = query["flow"] ?: "",
                realityPublicKey = query["pbk"] ?: "",
                realityShortId = query["sid"] ?: "",
                realitySpiderX = query["spx"] ?: "",
                allowInsecure = query["allowInsecure"] == "1"
            )
            return KrConfig(name = query["remarks"] ?: "#VLESS Config", type = ConfigType.VLESS, xray = xray)
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseTrojanLink(link: String): KrConfig? {
        try {
            val uri = android.net.Uri.parse(link)
            val password = uri.getUserInfo() ?: ""
            val host = uri.host ?: ""
            val port = uri.port.ifNegative(443)
            
            val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) }
            
            val xray = XrayConfig(
                protocol = XrayProtocol.TROJAN,
                host = host,
                port = port,
                uuid = password,
                security = when (query["security"]) { "tls" -> XraySecurity.TLS; else -> XraySecurity.NONE },
                network = when (query["type"]) { "ws" -> XrayNetwork.WS; "grpc" -> XrayNetwork.GRPC; else -> XrayNetwork.TCP },
                sni = query["sni"] ?: "",
                fingerprint = query["fp"] ?: "",
                path = query["path"] ?: "",
                hostHeader = query["host"] ?: "",
                allowInsecure = query["allowInsecure"] == "1"
            )
            return KrConfig(name = query["remarks"] ?: "#Trojan Config", type = ConfigType.TROJAN, xray = xray)
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseSshLink(link: String): KrConfig? {
        try {
            val uri = android.net.Uri.parse(link)
            val userInfo = uri.getUserInfo()?.split(":") ?: listOf()
            val username = userInfo.getOrNull(0) ?: ""
            val password = userInfo.getOrNull(1) ?: ""
            val host = uri.host ?: ""
            val port = uri.port.ifNegative(22)
            
            val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) }
            
            val ssh = SshConfig(
                host = host,
                port = port,
                username = username,
                password = password,
                payload = query["payload"] ?: "",
                sni = query["sni"] ?: "",
                payloadTemplateId = query["template"] ?: ""
            )
            return KrConfig(name = query["remarks"] ?: "#SSH Config", type = ConfigType.SSH, ssh = ssh)
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseKrVpnLink(link: String): KrConfig? {
        // krvpn://base64encodedjson
        try {
            val base64 = link.substringAfter("krvpn://")
            val jsonStr = String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP), "UTF-8")
            return json.decodeFromString<KrConfig>(jsonStr)
        } catch (e: Exception) {
            return null
        }
    }

    // ============ LINK GENERATION ============

    /**
     * Generate shareable link for config
     */
    suspend fun generateLink(config: KrConfig): String = withContext(Dispatchers.IO) {
        return when (config.type) {
            ConfigType.VMESS -> generateVmessLink(config)
            ConfigType.VLESS -> generateVlessLink(config)
            ConfigType.TROJAN -> generateTrojanLink(config)
            ConfigType.SSH -> generateSshLink(config)
            ConfigType.LOAD_BALANCE -> generateKrVpnLink(config)
            else -> generateKrVpnLink(config)
        }
    }

    private fun generateVmessLink(config: KrConfig): String {
        val xray = config.xray ?: return ""
        val vmess = VmessLink(
            v = "2",
            ps = config.name,
            add = xray.host,
            port = xray.port.toString(),
            id = xray.uuid,
            aid = xray.alterId.toString(),
            scy = "auto",
            net = when (xray.network) { XrayNetwork.WS -> "ws"; XrayNetwork.GRPC -> "grpc"; XrayNetwork.H2 -> "h2"; XrayNetwork.QUIC -> "quic"; else -> "tcp" },
            type = "none",
            host = xray.hostHeader,
            path = xray.path,
            tls = when (xray.security) { XraySecurity.TLS -> "tls"; XraySecurity.REALITY -> "reality"; else -> "" },
            sni = xray.sni,
            fp = xray.fingerprint,
            allowInsecure = if (xray.allowInsecure) "1" else "0"
        )
        val json = json.encodeToString(vmess)
        val base64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
        return "vmess://$base64"
    }

    private fun generateVlessLink(config: KrConfig): String {
        val xray = config.xray ?: return ""
        val uri = android.net.Uri.Builder()
            .scheme("vless")
            .encodedUserInfo(xray.uuid)
            .authority("${xray.host}:${xray.port}")
            .appendQueryParameter("security", when (xray.security) { XraySecurity.TLS -> "tls"; XraySecurity.REALITY -> "reality"; else -> "none" })
            .appendQueryParameter("type", when (xray.network) { XrayNetwork.WS -> "ws"; XrayNetwork.GRPC -> "grpc"; XrayNetwork.H2 -> "h2"; XrayNetwork.QUIC -> "quic"; else -> "tcp" })
            .apply {
                if (xray.sni.isNotBlank()) appendQueryParameter("sni", xray.sni)
                if (xray.fingerprint.isNotBlank()) appendQueryParameter("fp", xray.fingerprint)
                if (xray.path.isNotBlank()) appendQueryParameter("path", xray.path)
                if (xray.hostHeader.isNotBlank()) appendQueryParameter("host", xray.hostHeader)
                if (xray.flow.isNotBlank()) appendQueryParameter("flow", xray.flow)
                if (xray.realityPublicKey.isNotBlank()) appendQueryParameter("pbk", xray.realityPublicKey)
                if (xray.realityShortId.isNotBlank()) appendQueryParameter("sid", xray.realityShortId)
                if (xray.realitySpiderX.isNotBlank()) appendQueryParameter("spx", xray.realitySpiderX)
                appendQueryParameter("remarks", config.name)
            }
            .build()
        return uri.toString()
    }

    private fun generateTrojanLink(config: KrConfig): String {
        val xray = config.xray ?: return ""
        val uri = android.net.Uri.Builder()
            .scheme("trojan")
            .encodedUserInfo(xray.uuid)
            .authority("${xray.host}:${xray.port}")
            .appendQueryParameter("security", when (xray.security) { XraySecurity.TLS -> "tls"; else -> "none" })
            .appendQueryParameter("type", when (xray.network) { XrayNetwork.WS -> "ws"; XrayNetwork.GRPC -> "grpc"; else -> "tcp" })
            .apply {
                if (xray.sni.isNotBlank()) appendQueryParameter("sni", xray.sni)
                if (xray.fingerprint.isNotBlank()) appendQueryParameter("fp", xray.fingerprint)
                if (xray.path.isNotBlank()) appendQueryParameter("path", xray.path)
                if (xray.hostHeader.isNotBlank()) appendQueryParameter("host", xray.hostHeader)
                appendQueryParameter("remarks", config.name)
            }
            .build()
        return uri.toString()
    }

    private fun generateSshLink(config: KrConfig): String {
        val ssh = config.ssh ?: return ""
        val uri = android.net.Uri.Builder()
            .scheme("ssh")
            .encodedUserInfo("${ssh.username}:${ssh.password}")
            .authority("${ssh.host}:${ssh.port}")
            .apply {
                if (ssh.payload.isNotBlank()) appendQueryParameter("payload", ssh.payload)
                if (ssh.sni.isNotBlank()) appendQueryParameter("sni", ssh.sni)
                if (ssh.payloadTemplateId.isNotBlank()) appendQueryParameter("template", ssh.payloadTemplateId)
                appendQueryParameter("remarks", config.name)
            }
            .build()
        return uri.toString()
    }

    private fun generateKrVpnLink(config: KrConfig): String {
        val json = json.encodeToString(config)
        val base64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
        return "krvpn://$base64"
    }

    // ============ PERSISTENCE ============

    private fun isEncrypted(str: String): Boolean {
        // Heuristic: encrypted configs are Base64 and decode to binary (not valid JSON)
        try {
            val decoded = android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
            return decoded.size > 100 && (decoded[0] != 0x7B.toByte()) // Not starting with '{'
        } catch (e: Exception) {
            return false
        }
    }

    private fun persistConfigs() {
        val editor = prefs.edit()
        configsMap.values.forEach { config ->
            val jsonString = json.encodeToString(config)
            val encrypted = security.encryptConfig(jsonString)
            editor.putString(config.id, encrypted)
        }
        // Remove deleted configs
        val existingKeys = prefs.all.keys.toSet()
        val currentKeys = configsMap.keys.toSet()
        (existingKeys - currentKeys).forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loadConfigs() {
        prefs.all.forEach { (key, value) ->
            try {
                val encrypted = value as String
                val decrypted = security.decryptConfig(encrypted)
                val config = json.decodeFromString<KrConfig>(decrypted)
                configsMap[key] = config
            } catch (e: Exception) {
                // Skip corrupted entries
            }
        }
        _configs.value = configsMap.values.toList().sortedByDescending { it.updatedAt }
    }

    // ============ VMess Link Data Class ============

    @kotlinx.serialization.Serializable
    private data class VmessLink(
        val v: String,
        val ps: String,
        val add: String,
        val port: String,
        val id: String,
        val aid: String,
        val scy: String,
        val net: String,
        val type: String,
        val host: String,
        val path: String,
        val tls: String,
        val sni: String,
        val fp: String,
        val allowInsecure: String
    )
}
