package com.example.leadhunters.data.remote.model

import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("name") val name: String
)

data class AuthResponse(
    @SerializedName("token") val token: String
)
