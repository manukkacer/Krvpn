package com.krvpn.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.krvpn.app.presentation.viewmodel.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.lifecycle.HiltViewModelFactory
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(ActivityRetainedComponent::class)
object ViewModelModule {

    @Provides
    @Singleton
    fun provideViewModelFactory(
        homeViewModel: HomeViewModel,
        configViewModel: ConfigViewModel,
        settingsViewModel: SettingsViewModel,
        diagnosticsViewModel: DiagnosticsViewModel,
        statisticsViewModel: StatisticsViewModel
    ): HiltViewModelFactory = HiltViewModelFactory(
        mapOf(
            HomeViewModel::class.java to { homeViewModel },
            ConfigViewModel::class.java to { configViewModel },
            SettingsViewModel::class.java to { settingsViewModel },
            DiagnosticsViewModel::class.java to { diagnosticsViewModel },
            StatisticsViewModel::class.java to { statisticsViewModel }
        )
    )
}
