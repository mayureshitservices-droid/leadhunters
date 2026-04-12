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
        com.example.leadhunters.util.CrashReporter.log("Starting device registration")
        return try {
            val deviceId = deviceProvider.getDeviceId()
            val deviceName = deviceProvider.getDeviceName()

            val request = DeviceRegistrationRequest(
                deviceId = deviceId,
                name = deviceName
            )

            val response = authApiService.registerDevice(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.token.isNotEmpty()) {
                    val token = body.token
                    authPreferences.saveAuthToken(token)
                    authPreferences.saveDeviceId(deviceId)
                    com.example.leadhunters.util.CrashReporter.log("Device registered successfully")
                    Result.success(token)
                } else {
                    val errorMsg = "Registration failed: Empty response body or token"
                    com.example.leadhunters.util.CrashReporter.log("ERROR: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = "Registration failed with code: ${response.code()}"
                com.example.leadhunters.util.CrashReporter.log("ERROR: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            com.example.leadhunters.util.CrashReporter.logError(e, "Unexpected error during device registration")
            Result.failure(e)
        }
    }

    override suspend fun isRegistered(): Boolean {
        return authPreferences.authToken.first() != null
    }
}
