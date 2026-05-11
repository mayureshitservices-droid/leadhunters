package com.example.leadhunters.data.remote.api

import com.example.leadhunters.data.remote.model.AuthResponse
import com.example.leadhunters.data.remote.model.DeviceRegistrationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("/api/auth/deviceregistration")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<AuthResponse>

    @POST("/api/auth/heartbeat")
    suspend fun heartbeat(): Response<HeartbeatResponse>
}

@androidx.annotation.Keep
data class HeartbeatResponse(
    @com.google.gson.annotations.SerializedName("success") val success: Boolean,
    @com.google.gson.annotations.SerializedName("deletedLeads") val deletedLeads: List<String>? = null
)
