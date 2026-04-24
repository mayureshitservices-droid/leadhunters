package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.remote.api.WorkApiService
import com.example.leadhunters.data.remote.model.CallLogSyncRequest
import com.example.leadhunters.util.AnalyticsHelper
import kotlinx.coroutines.flow.Flow
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

    override suspend fun syncLeads(): Result<Unit> {
        return try {
            val response = apiService.getLeads()
            if (response.isSuccessful) {
                val leadsDto = response.body()?.leads ?: emptyList()
                val entities = leadsDto.map { dto ->
                    Lead(
                        id = dto.id,
                        name = dto.name,
                        phoneNumber = dto.phone,
                        status = dto.status,
                        businessOwnerId = dto.businessOwnerId,
                        businessOwnerName = dto.businessOwnerName
                    )
                }
                teleCallerDao.insertLeads(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch leads: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncCallLog(
        localLogId: Long,
        leadId: String,
        durationSeconds: Int,
        callStatus: String,
        outcome: String?,
        notes: String?
    ): Result<String?> {
        return try {
            val request = CallLogSyncRequest(
                localLogId = localLogId,
                leadId = leadId,
                durationSeconds = durationSeconds,
                callStatus = callStatus,
                outcome = outcome,
                notes = notes
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
            if (!file.exists()) return Result.failure(Exception("Recording file not found"))

            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("recording", file.name, requestFile)
            val logIdBody = serverLogId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadRecording(logIdBody, body)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to upload recording: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
