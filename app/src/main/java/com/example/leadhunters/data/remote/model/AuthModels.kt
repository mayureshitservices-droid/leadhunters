package com.example.leadhunters.data.remote.model

import com.google.gson.annotations.SerializedName

@androidx.annotation.Keep
data class DeviceRegistrationRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("name") val name: String
)

@androidx.annotation.Keep
data class AuthResponse(
    @SerializedName("token") val token: String
)
