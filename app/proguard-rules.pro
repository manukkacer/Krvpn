# KRVPN ProGuard Rules

# --- Keep Application & Hilt ---
-keep class com.krvpn.app.KrApplication { *; }
-keep class com.krvpn.app.di.** { *; }

# --- Keep Data Models (Serialization) ---
-keep class com.krvpn.app.data.model.** { *; }
-keepclassmembers class com.krvpn.app.data.model.** {
    <fields>;
    <methods>;
}
# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.json.** { *; }

# --- Keep Services ---
-keep class com.krvpn.app.service.** { *; }

# --- Keep Native JNI Methods ---
-keep class com.krvpn.app.nativebridge.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Keep Hilt Generated ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# --- OkHttp & Retrofit ---
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# --- Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembers class * implements kotlinx.coroutines.CoroutineScope { *; }

# --- DataStore ---
-keep class androidx.datastore.** { *; }

# --- Security Crypto ---
-keep class androidx.security.crypto.** { *; }

# --- Coil ---
-keep class coil3.** { *; }

# --- Timber ---
-keep class timber.log.** { *; }

# --- Compose ---
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }

# --- Remove Logging in Release ---
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# --- Keep Parcelable ---
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# --- Keep Enum ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep R8/ProGuard from removing unused resources ---
# (Handled by shrinkResources)

# --- Native Libraries ---
-keep class com.krvpn.app.security.KrSecurity { *; }
-keep class com.krvpn.app.nativebridge.** { *; }
