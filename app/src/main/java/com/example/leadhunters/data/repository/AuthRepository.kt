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
    suspend fun sendHeartbeat(): Result<List<String>>
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
            com.example.leadhunters.util.CrashReporter.log("Registering device: $deviceName (ID: $deviceId)")

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
                val errorBody = response.errorBody()?.string() ?: response.message()
                val errorMsg = "Registration failed with code: ${response.code()}, body: $errorBody"
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

    override suspend fun sendHeartbeat(): Result<List<String>> {
        return try {
            val response = authApiService.heartbeat()
            if (response.isSuccessful) {
                Result.success(response.body()?.deletedLeads ?: emptyList())
            } else {
                Result.failure(Exception("Heartbeat failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
