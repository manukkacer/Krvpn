package com.krvpn.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.core.DataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.krvpn.app.data.local.ConfigRepository
import com.krvpn.app.data.local.SettingsRepository
import com.krvpn.app.data.local.preferencesKeys
import com.krvpn.app.data.security.KrSecurity
import com.krvpn.app.di.hiltModule
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class KrApplication : Application() {

    // Coroutine scope for app-wide operations
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // DataStore for preferences
    val settingsDataStore: DataStore<Preferences> by lazy {
        preferencesDataStore(name = "krvpn_settings")
    }

    // EncryptedSharedPreferences for sensitive data
    val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "krvpn_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Repositories (initialized after Hilt)
    lateinit var configRepository: ConfigRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var securityManager: KrSecurity

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }

        // Initialize KrSecurity (native library loader + keystore)
        securityManager = KrSecurity(this)

        // Initialize repositories
        configRepository = ConfigRepository(this, securityManager)
        settingsRepository = SettingsRepository(this)

        // Apply saved theme
        applyTheme()

        // Apply saved locale
        applyLocale()

        // Setup default uncaught exception handler
        setupCrashHandler()

        Timber.tag("KrApplication").i("KRVPN Application started v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    private fun applyTheme() {
        val themeMode = settingsRepository.getThemeMode()
        when (themeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun applyLocale() {
        val localeTag = settingsRepository.getLocaleTag()
        if (localeTag.isNotBlank()) {
            val locale = LocaleList.forLanguageTags(localeTag)
            val config = Configuration(resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(locale)
            } else {
                config.setLocale(locale[0])
            }
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.tag("CrashHandler").e(throwable, "Uncaught exception in thread: ${thread.name}")
            // Save crash log to encrypted prefs for later retrieval
            encryptedPrefs.edit().putString("last_crash", throwable.printStackTraceToString()).apply()
            // Call default handler
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            // Clear image caches, etc.
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun provideContext(@Suppress("UNUSED_PARAMETER") app: KrApplication): Context = app
    }
}

// Custom Timber tree for crash reporting in release
private class CrashReportingTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
        if (priority == Log.ERROR || priority == Log.WARN) {
            // In production: send to crash reporting service (Firebase Crashlytics, Sentry, etc.)
            // For now, just log to encrypted storage
        }
    }
}

// Extension to convert throwable to string
private fun Throwable.printStackTraceToString(): String {
    val sw = java.io.StringWriter()
    val pw = java.io.PrintWriter(sw)
    printStackTrace(pw)
    return sw.toString()
}

// Preferences keys
internal object preferencesKeys {
    val THEME_MODE = preferencesKey<Int>("theme_mode")
    val LOCALE_TAG = preferencesKey<String>("locale_tag")
    val AUTO_RECONNECT = preferencesKey<Boolean>("auto_reconnect")
    val BATTERY_OPTIMIZATION_REQUESTED = preferencesKey<Boolean>("battery_opt_requested")
    val NOTIFICATIONS_ENABLED = preferencesKey<Boolean>("notifications_enabled")
    val LAST_CONNECTED_CONFIG_ID = preferencesKey<String>("last_config_id")
}
