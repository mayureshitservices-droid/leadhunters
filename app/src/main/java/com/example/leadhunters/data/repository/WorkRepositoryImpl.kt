package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.dao.TeleCallerDao
import android.util.Log
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.remote.api.WorkApiService
import com.example.leadhunters.data.remote.model.CallLogSyncRequest
import com.example.leadhunters.data.remote.model.CampaignDto
import com.example.leadhunters.data.remote.model.ClaimCampaignRequest
import com.example.leadhunters.data.remote.model.TelecallerStatusRequest
import com.example.leadhunters.util.AnalyticsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkRepositoryImpl @Inject constructor(
    private val apiService: WorkApiService,
    private val teleCallerDao: TeleCallerDao,
    private val analyticsHelper: AnalyticsHelper
) : WorkRepository {

    override fun getLeads(): Flow<List<Lead>> = teleCallerDao.getAllLeads()

    override fun getCampaigns(): Flow<Result<List<CampaignDto>>> = flow {
        try {
            val response = apiService.getCampaigns()
            if (response.isSuccessful) {
                emit(Result.success(response.body()?.campaigns ?: emptyList()))
            } else {
                emit(Result.failure(Exception("Failed to fetch campaigns: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun claimCampaign(campaignName: String): Result<Int> {
        return try {
            val request = ClaimCampaignRequest(campaignName = campaignName)
            val response = apiService.claimCampaign(request)
            if (response.isSuccessful) {
                val successResponse = response.body()
                if (successResponse?.success == true) {
                    val serverLeads = successResponse.leads
                    if (serverLeads != null) {
                        val entities = serverLeads.map { dto ->
                            Lead(
                                id = dto.id,
                                name = dto.name,
                                phoneNumber = dto.phone,
                                status = dto.status,
                                businessOwnerId = dto.businessOwnerId,
                                campaignName = dto.campaignName ?: campaignName,
                                additionalData = dto.additionalData
                            )
                        }
                        teleCallerDao.clearAndInsertLeads(entities)
                    } else {
                        Log.w("WorkRepository", "claimCampaign success but no leads returned for campaign: $campaignName")
                    }
                    Result.success(successResponse.claimedCount)
                } else {
                    Result.failure(Exception("Backend claim operation failed."))
                }
            } else {
                Result.failure(Exception("Failed to claim campaign: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncCallLog(
        localLogId: String,
        leadId: String,
        durationSeconds: Int,
        callStatus: String,
        outcome: String?,
        notes: String?,
        nextReminderTime: Long?
    ): Result<String?> {
        return try {
            val request = CallLogSyncRequest(
                localLogId = localLogId,
                leadId = leadId,
                durationSeconds = durationSeconds,
                callStatus = callStatus,
                outcome = outcome,
                notes = notes,
                nextReminderTime = nextReminderTime
            )
            val response = apiService.syncCallLog(request)
            if (response.isSuccessful) {
                analyticsHelper.logApiSyncResult(leadId, response.code(), true, null)
                Result.success(response.body()?.logId)
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                analyticsHelper.logApiSyncResult(leadId, response.code(), false, errorBody)
                Result.failure(Exception("Failed to sync call log [${response.code()}]: $errorBody"))
            }
        } catch (e: Exception) {
            analyticsHelper.logApiSyncResult(leadId, -1, false, e.message)
            Result.failure(e)
        }
    }

    override suspend fun uploadRecording(
        serverLogId: String,
        recordingPath: String
    ): Result<Unit> {
        return try {
            val file = java.io.File(recordingPath)
            if (!file.exists()) {
                Log.e("WorkRepository", "Recording file NOT FOUND at: $recordingPath")
                return Result.failure(Exception("Recording file not found"))
            }

            if (!file.canRead()) {
                Log.e("WorkRepository", "Recording file NOT READABLE at: $recordingPath (Check permissions!)")
                return Result.failure(Exception("Recording file not readable"))
            }

            Log.i("WorkRepository", "Preparing to upload file: ${file.name} (${file.length()} bytes)")

            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("recording", file.name, requestFile)
            val logIdBody = serverLogId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadRecording(logIdBody, body)
            if (response.isSuccessful) {
                Log.i("WorkRepository", "Recording uploaded successfully for log $serverLogId")
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e("WorkRepository", "FAILED to upload recording. Code: ${response.code()} | Error: $errorMsg")
                Result.failure(Exception("Failed to upload recording: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("WorkRepository", "CRITICAL ERROR during recording upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteLeadsLocally(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.chunked(900).forEach { chunk ->
            teleCallerDao.deleteLeadsByIds(chunk)
        }
        Log.i("WorkRepository", "Locally deleted ${ids.size} leads via heartbeat command")
    }

    override suspend fun updateTelecallerStatus(status: String) {
        withContext(Dispatchers.IO) {
            val request = TelecallerStatusRequest(
                status = status,
                timestamp = System.currentTimeMillis()
            )
            val response = apiService.updateStatus(request)
            if (response.isSuccessful) {
                Log.i("WorkRepository", "Telecaller status updated to $status")
            } else {
                Log.w("WorkRepository", "Failed to update status: ${response.code()} ${response.message()}")
            }
        }
    }
}
