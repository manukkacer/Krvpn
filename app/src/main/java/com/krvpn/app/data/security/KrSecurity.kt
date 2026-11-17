package com.krvpn.app.data.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * KRVPN Security Manager
 * Handles:
 * - Native library loading (libkrvpn-security.so)
 * - Config encryption/decryption using Android Keystore + AES-GCM
 * - Device binding & anti-tamper checks
 * - Secure random generation
 * - App signature verification
 */
class KrSecurity(private val context: Context) {

    companion object {
        private const val TAG = "KrSecurity"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "krvpn_master_key"
        private const val PBKDF2_ITERATIONS = 100000
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val SALT_LENGTH = 32

        // Native library name
        private const val NATIVE_LIB_NAME = "krvpn-security"
    }

    // Native library loaded flag
    private var nativeLoaded = false
    private var nativeLoadError: Throwable? = null

    init {
        loadNativeLibrary()
        initKeystore()
    }

    /**
     * Load native security library
     */
    private fun loadNativeLibrary() {
        try {
            System.loadLibrary(NATIVE_LIB_NAME)
            nativeLoaded = true
            Log.i(TAG, "Native library '$NATIVE_LIB_NAME' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            nativeLoadError = e
            Log.w(TAG, "Native library '$NATIVE_LIB_NAME' not found: ${e.message}. Using pure Kotlin fallback.")
        } catch (e: Exception) {
            nativeLoadError = e
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    /**
     * Initialize Android Keystore master key
     */
    private fun initKeystore() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                generateMasterKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize keystore", e)
        }
    }

    private fun generateMasterKey() {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGen.init(spec)
        keyGen.generateKey()
    }

    // ============ CONFIG ENCRYPTION/DECRYPTION ============

    /**
     * Encrypt config JSON to encrypted bytes
     * Returns Base64 encoded string: salt:iv:ciphertext:tag
     */
    suspend fun encryptConfig(configJson: String, password: String? = null): String = withContext(Dispatchers.IO) {
        val plaintext = configJson.toByteArray(StandardCharsets.UTF_8)
        val encrypted = encryptData(plaintext, password)
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypt config from Base64 encoded string
     */
    suspend fun decryptConfig(encryptedBase64: String, password: String? = null): String = withContext(Dispatchers.IO) {
        val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decrypted = decryptData(encrypted, password)
        String(decrypted, StandardCharsets.UTF_8)
    }

    /**
     * Encrypt arbitrary data
     * Format: [salt(32)][iv(12)][ciphertext][tag(16)]
     */
    private fun encryptData(plaintext: ByteArray, password: String?): ByteArray {
        // Generate salt
        val salt = generateSecureRandom(SALT_LENGTH)
        
        // Derive key from password + salt (or use master key)
        val key = if (password != null && password.isNotBlank()) {
            deriveKeyPBKDF2(password, salt)
        } else {
            getMasterKey()
        }

        // Generate IV
        val iv = generateSecureRandom(GCM_IV_LENGTH)

        // Encrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)

        // Combine: salt + iv + ciphertext (includes tag at end for GCM)
        val output = ByteArrayOutputStream()
        output.write(salt)
        output.write(iv)
        output.write(ciphertext)
        return output.toByteArray()
    }

    /**
     * Decrypt data encrypted by encryptData
     */
    private fun decryptData(encrypted: ByteArray, password: String?): ByteArray {
        if (encrypted.size < SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw SecurityException("Invalid encrypted data: too short")
        }

        // Extract components
        val salt = encrypted.copyOfRange(0, SALT_LENGTH)
        val iv = encrypted.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, encrypted.size)

        // Derive key
        val key = if (password != null && password.isNotBlank()) {
            deriveKeyPBKDF2(password, salt)
        } else {
            getMasterKey()
        }

        // Decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Derive AES key using PBKDF2
     */
    private fun deriveKeyPBKDF2(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Get master key from Android Keystore
     */
    private fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    // ============ SECURE RANDOM ============

    fun generateSecureRandom(length: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }

    fun generateSecureRandomHex(length: Int): String {
        return generateSecureRandom(length).joinToString("") { "%02x".format(it) }
    }

    // ============ DEVICE BINDING & ANTI-TAMPER ============

    /**
     * Perform environment security checks
     * Returns bitmask of detected issues
     */
    fun environmentFlags(): Int {
        var flags = 0

        // Check root
        if (isDeviceRooted()) flags |= ENV_ROOTED

        // Check emulator
        if (isEmulator()) flags |= ENV_EMULATOR

        // Check debugger
        if (isDebuggerConnected()) flags |= ENV_DEBUGGER

        // Check hook frameworks (Frida, Xposed)
        if (isHooked()) flags |= ENV_HOOKED

        // Check app signature
        if (!verifyAppSignature()) flags |= ENV_SIGNATURE_MISMATCH

        // Check native library integrity
        if (!verifyNativeLibs()) flags |= ENV_NATIVE_TAMPERED

        // Native checks (if library loaded)
        if (nativeLoaded) {
            flags |= nativeEnvironmentFlags()
        }

        return flags
    }

    companion object {
        const val ENV_ROOTED = 1 shl 0
        const val ENV_EMULATOR = 1 shl 1
        const val ENV_DEBUGGER = 1 shl 2
        const val ENV_HOOKED = 1 shl 3
        const val ENV_SIGNATURE_MISMATCH = 1 shl 4
        const val ENV_NATIVE_TAMPERED = 1 shl 5
        const val ENV_UNSAFE = 1 shl 6
    }

    private fun isDeviceRooted(): Boolean {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/su", "/system/bin/.ext/su", "/system/xbin/.ext/su"
        )
        return suPaths.any { java.io.File(it).exists() } ||
               java.io.File("/system/app/Superuser.apk").exists() ||
               java.io.File("/system/app/MagiskManager.apk").exists()
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK") ||
               Build.HARDWARE.contains("goldfish") ||
               Build.HARDWARE.contains("ranchu") ||
               Build.PRODUCT.contains("sdk") ||
               Build.BRAND.contains("generic")
    }

    private fun isDebuggerConnected(): Boolean = android.os.Debug.isDebuggerConnected()

    private fun isHooked(): Boolean {
        // Check for Frida
        val fridaFiles = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida-helper",
            "/system/lib/libfrida.so",
            "/system/lib64/libfrida.so"
        )
        if (fridaFiles.any { java.io.File(it).exists() }) return true

        // Check for Xposed
        val xposedProps = listOf(
            "xposed",
            "de.robv.android.xposed",
            "io.va.exposed"
        )
        val props = System.getProperties()
        if (xposedProps.any { props.keys().any { it.toString().contains(it, ignoreCase = true) } }) return true

        // Check for other hook frameworks
        val suspiciousLibs = listOf("libsubstrate.so", "libdobby.so", "libwhale.so")
        return suspiciousLibs.any { System.mapLibraryName(it).let { java.io.File("/system/lib64/$it").exists() || java.io.File("/system/lib/$it").exists() } }
    }

    private fun verifyAppSignature(): Boolean {
        try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signingInfo.apkContentsSigners
            if (signatures.isEmpty()) return false

            // Expected signature hash (SHA-256 of release cert)
            // TODO: Replace with your actual release certificate SHA-256
            val expectedHashes = setOf(
                "YOUR_RELEASE_CERT_SHA256_HERE"
            )

            return signatures.any { sig ->
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                hash in expectedHashes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            return false
        }
    }

    private fun verifyNativeLibs(): Boolean {
        // Verify native libraries haven't been tampered with
        // Could check file hashes against known values
        return true // Implement based on your build pipeline
    }

    // ============ NATIVE METHODS (JNI) ============

    /**
     * Native environment checks (implemented in libkrvpn-security.so)
     * Returns additional flags from native side
     */
    external fun nativeEnvironmentFlags(): Int

    /**
     * Native config encryption (faster for large configs)
     */
    external fun nativeEncryptConfig(data: ByteArray, password: String): ByteArray

    /**
     * Native config decryption
     */
    external fun nativeDecryptConfig(data: ByteArray, password: String): ByteArray

    /**
     * Native device binding check
     */
    external fun nativeDeviceBinding(packageName: String, signatureHash: String): Int

    /**
     * Verify app hasn't been modified
     */
    external fun nativeVerifyIntegrity(): Boolean

    // ============ PASSWORD HASHING ============

    /**
     * Hash password for storage (using PBKDF2)
     */
    fun hashPassword(password: String): String {
        val salt = generateSecureRandom(SALT_LENGTH)
        val key = deriveKeyPBKDF2(password, salt)
        val output = ByteArrayOutputStream()
        output.write(salt)
        output.write(key.encoded)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Verify password against stored hash
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        try {
            val decoded = Base64.decode(storedHash, Base64.NO_WRAP)
            if (decoded.size != SALT_LENGTH + AES_KEY_SIZE / 8) return false
            
            val salt = decoded.copyOfRange(0, SALT_LENGTH)
            val expectedKey = decoded.copyOfRange(SALT_LENGTH, decoded.size)
            val key = deriveKeyPBKDF2(password, salt)
            return MessageDigest.isEqual(key.encoded, expectedKey)
        } catch (e: Exception) {
            return false
        }
    }

    // ============ UTILITY ============

    fun isNativeLibraryLoaded(): Boolean = nativeLoaded

    fun getNativeLoadError(): Throwable? = nativeLoadError

    /**
     * Get app signature hash for native verification
     */
    fun getAppSignatureHash(): String {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val sig = packageInfo.signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
            return sig?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { "%02x".format(it) } } ?: ""
        } catch (e: Exception) {
            return ""
        }
    }
}
