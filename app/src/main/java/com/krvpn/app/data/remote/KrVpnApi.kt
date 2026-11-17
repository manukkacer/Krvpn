package com.krvpn.app.data.remote

import com.krvpn.app.data.model.KrConfig
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import retrofit2.http.*

interface KrVpnApi {

    @GET("configs")
    suspend fun getConfigs(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ConfigListResponse>

    @GET("configs/{id}")
    suspend fun getConfig(@Path("id") id: String): Response<KrConfig>

    @POST("configs")
    suspend fun createConfig(@Body config: KrConfig): Response<KrConfig>

    @PUT("configs/{id}")
    suspend fun updateConfig(@Path("id") id: String, @Body config: KrConfig): Response<KrConfig>

    @DELETE("configs/{id}")
    suspend fun deleteConfig(@Path("id") id: String): Response<Unit>

    @GET("servers")
    suspend fun getServers(): Response<ServerListResponse>

    @GET("subscription/{token}")
    suspend fun getSubscription(@Path("token") token: String): Response<SubscriptionResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}

data class ConfigListResponse(
    val data: List<KrConfig>,
    val total: Int,
    val page: Int,
    val limit: Int
)

data class ServerListResponse(
    val servers: List<ServerInfo>
)

data class ServerInfo(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val country: String,
    val city: String,
    val load: Int,
    val ping: Int
)

data class SubscriptionResponse(
    val configs: List<KrConfig>,
    val expiresAt: Long,
    val trafficLimit: Long,
    val trafficUsed: Long
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)
