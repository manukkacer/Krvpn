package com.krvpn.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutations
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await

/**
 * Settings Repository - Manages app preferences via DataStore
 */
class SettingsRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    // Preference Keys
    private object Keys {
        val THEME_MODE = preferencesKey<Int>("theme_mode")
        val LOCALE_TAG = stringPreferencesKey("locale_tag")
        val AUTO_RECONNECT = preferencesKey<Boolean>("auto_reconnect")
        val BATTERY_OPTIMIZATION_REQUESTED = preferencesKey<Boolean>("battery_opt_requested")
        val NOTIFICATIONS_ENABLED = preferencesKey<Boolean>("notifications_enabled")
        val LOG_LEVEL = preferencesKey<Int>("log_level")
        val MAX_LOG_ENTRIES = preferencesKey<Int>("max_log_entries")
        val ENABLE_PACKET_CAPTURE = preferencesKey<Boolean>("enable_packet_capture")
        val DNS_OVER_HTTPS = preferencesKey<Boolean>("dns_over_https")
        val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
        val PREFER_IPV6 = preferencesKey<Boolean>("prefer_ipv6")
        val MTU = preferencesKey<Int>("mtu")
        val TCP_FAST_OPEN = preferencesKey<Boolean>("tcp_fast_open")
        val UDP_ENABLED = preferencesKey<Boolean>("udp_enabled")
        val LAST_CONNECTED_CONFIG_ID = stringPreferencesKey("last_connected_config_id")
    }

    // ============ FLOWS ============

    val themeMode = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.THEME_MODE] ?: 0 }

    val localeTag = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.LOCALE_TAG] ?: "" }

    val autoReconnect = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.AUTO_RECONNECT] ?: true }

    val batteryOptimizationRequested = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.BATTERY_OPTIMIZATION_REQUESTED] ?: false }

    val notificationsEnabled = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val logLevel = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.LOG_LEVEL] ?: 2 }

    val maxLogEntries = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.MAX_LOG_ENTRIES] ?: 5000 }

    val enablePacketCapture = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.ENABLE_PACKET_CAPTURE] ?: false }

    val dnsOverHttps = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.DNS_OVER_HTTPS] ?: false }

    val customDohUrl = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.CUSTOM_DOH_URL] ?: "" }

    val preferIpv6 = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.PREFER_IPV6] ?: false }

    val mtu = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.MTU] ?: 1500 }

    val tcpFastOpen = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.TCP_FAST_OPEN] ?: false }

    val udpEnabled = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.UDP_ENABLED] ?: true }

    val lastConnectedConfigId = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.LAST_CONNECTED_CONFIG_ID] ?: "" }

    // ============ SYNCHRONOUS GETTERS (for non-coroutine contexts) ============

    fun getThemeMode(): Int = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.THEME_MODE] ?: 0 }
        .firstOrNull()?.await() ?: 0

    fun getLocaleTag(): String = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.LOCALE_TAG] ?: "" }
        .firstOrNull()?.await() ?: ""

    fun getAutoReconnect(): Boolean = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.AUTO_RECONNECT] ?: true }
        .firstOrNull()?.await() ?: true

    fun getBatteryOptimizationRequested(): Boolean = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.BATTERY_OPTIMIZATION_REQUESTED] ?: false }
        .firstOrNull()?.await() ?: false

    fun getNotificationsEnabled(): Boolean = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
        .firstOrNull()?.await() ?: true

    fun getLastConnectedConfigId(): String = dataStore.data
        .catch { emptyPreferences() }
        .map { it[Keys.LAST_CONNECTED_CONFIG_ID] ?: "" }
        .firstOrNull()?.await() ?: ""

    // ============ SETTERS ============

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.coerceIn(0..2) }
    }

    suspend fun setLocaleTag(tag: String) {
        dataStore.edit { it[Keys.LOCALE_TAG] = tag }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setBatteryOptimizationRequested(requested: Boolean) {
        dataStore.edit { it[Keys.BATTERY_OPTIMIZATION_REQUESTED] = requested }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLogLevel(level: Int) {
        dataStore.edit { it[Keys.LOG_LEVEL] = level.coerceIn(0..4) }
    }

    suspend fun setMaxLogEntries(max: Int) {
        dataStore.edit { it[Keys.MAX_LOG_ENTRIES] = max.coerceIn(100..10000) }
    }

    suspend fun setEnablePacketCapture(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_PACKET_CAPTURE] = enabled }
    }

    suspend fun setDnsOverHttps(enabled: Boolean) {
        dataStore.edit { it[Keys.DNS_OVER_HTTPS] = enabled }
    }

    suspend fun setCustomDohUrl(url: String) {
        dataStore.edit { it[Keys.CUSTOM_DOH_URL] = url }
    }

    suspend fun setPreferIpv6(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFER_IPV6] = enabled }
    }

    suspend fun setMtu(mtuValue: Int) {
        dataStore.edit { it[Keys.MTU] = mtuValue.coerceIn(576..9000) }
    }

    suspend fun setTcpFastOpen(enabled: Boolean) {
        dataStore.edit { it[Keys.TCP_FAST_OPEN] = enabled }
    }

    suspend fun setUdpEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.UDP_ENABLED] = enabled }
    }

    suspend fun setLastConnectedConfigId(configId: String) {
        dataStore.edit { it[Keys.LAST_CONNECTED_CONFIG_ID] = configId }
    }

    // ============ RESET ============

    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }
}
