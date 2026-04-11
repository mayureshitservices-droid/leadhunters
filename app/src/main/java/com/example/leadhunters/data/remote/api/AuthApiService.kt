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
}
