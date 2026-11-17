package com.krvpn.app.di

import com.jakewharton.retrofit2.kotlinx.serialization.asConverterFactory
import com.krvpn.app.data.model.KrConfig
import com.krvpn.app.data.remote.KrVpnApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.krvpn.example.com/") // TODO: Replace with actual API
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(asConverterFactory("application/json"))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): KrVpnApi = retrofit.create(KrVpnApi::class.java)
}
