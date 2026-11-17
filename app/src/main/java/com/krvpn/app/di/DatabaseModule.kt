package com.krvpn.app.di

import android.content.Context
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.krvpn.app.data.local.ConfigRepository
import com.krvpn.app.data.local.SettingsRepository
import com.krvpn.app.data.security.KrSecurity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.preferencesDataStore(name = "krvpn_settings")

    @Provides
    @Singleton
    fun provideConfigRepository(
        @ApplicationContext context: Context,
        security: KrSecurity
    ): ConfigRepository = ConfigRepository(context, security)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>
    ): SettingsRepository = SettingsRepository(context, dataStore)

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context): KrSecurity = KrSecurity(context)
}
