package com.example.leadhunters.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import com.google.gson.annotations.SerializedName

@androidx.annotation.Keep
data class AppVersionResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("mandatory") val mandatory: Boolean
)

interface AppApiService {
    @Headers("Cache-Control: no-cache")
    @GET("version")
    suspend fun getAppVersion(): Response<AppVersionResponse>
}
