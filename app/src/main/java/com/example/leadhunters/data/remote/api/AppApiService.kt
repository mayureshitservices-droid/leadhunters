package com.example.leadhunters.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import com.google.gson.annotations.SerializedName

data class AppVersionResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("mandatory") val mandatory: Boolean
)

interface AppApiService {
    @GET("api/app/version")
    suspend fun getAppVersion(): Response<AppVersionResponse>
}
