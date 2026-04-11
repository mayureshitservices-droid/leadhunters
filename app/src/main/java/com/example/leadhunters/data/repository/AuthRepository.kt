package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.AuthPreferences
import com.example.leadhunters.data.remote.api.AuthApiService
import com.example.leadhunters.data.remote.model.DeviceRegistrationRequest
import com.example.leadhunters.data.system.DeviceIdentifierProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    suspend fun registerDevice(): Result<String>
    suspend fun isRegistered(): Boolean
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val authPreferences: AuthPreferences,
    private val deviceProvider: DeviceIdentifierProvider
) : AuthRepository {

    override suspend fun registerDevice(): Result<String> {
        return try {
            val deviceId = deviceProvider.getDeviceId()
            val deviceName = deviceProvider.getDeviceName()

            val request = DeviceRegistrationRequest(
                deviceId = deviceId,
                name = deviceName
            )

            val response = authApiService.registerDevice(request)
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!.token
                authPreferences.saveAuthToken(token)
                authPreferences.saveDeviceId(deviceId)
                Result.success(token)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isRegistered(): Boolean {
        return authPreferences.authToken.first() != null
    }
}
