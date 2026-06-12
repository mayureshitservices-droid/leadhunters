package com.example.leadhunters.data.remote.api

import com.example.leadhunters.data.remote.model.CallLogSyncRequest
import com.example.leadhunters.data.remote.model.SyncResponse
import com.example.leadhunters.data.remote.model.CampaignsResponse
import com.example.leadhunters.data.remote.model.ClaimCampaignRequest
import com.example.leadhunters.data.remote.model.ClaimCampaignResponse
import com.example.leadhunters.data.remote.model.TelecallerStatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WorkApiService {
    @GET("/api/v1/work/campaigns")
    suspend fun getCampaigns(): Response<CampaignsResponse>

    @POST("/api/v1/work/campaigns/claim")
    suspend fun claimCampaign(
        @Body request: ClaimCampaignRequest
    ): Response<ClaimCampaignResponse>

    @POST("/api/v1/sync/call-log")
    suspend fun syncCallLog(
        @Body request: CallLogSyncRequest
    ): Response<SyncResponse>

    @retrofit2.http.Multipart
    @POST("/api/v1/sync/recording")
    suspend fun uploadRecording(
        @retrofit2.http.Part("log_id") logId: okhttp3.RequestBody,
        @retrofit2.http.Part recording: okhttp3.MultipartBody.Part
    ): Response<SyncResponse>

    @POST("/api/v1/telecaller/status")
    suspend fun updateStatus(
        @Body request: TelecallerStatusRequest
    ): Response<SyncResponse>
}
