package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.dao.TeleCallerDao
import android.util.Log
import android.content.Context
import android.provider.MediaStore
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.remote.api.WorkApiService
import com.example.leadhunters.data.remote.model.CallLogSyncRequest
import com.example.leadhunters.data.remote.model.CampaignDto
import com.example.leadhunters.data.remote.model.ClaimCampaignRequest
import com.example.leadhunters.data.remote.model.TelecallerStatusRequest
import com.example.leadhunters.util.AnalyticsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val analyticsHelper: AnalyticsHelper,
    @ApplicationContext private val context: Context
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
        nextReminderTime: Long?,
        closingFormat: String?,
        ptpAmount: Double?
    ): Result<String?> {
        return try {
            val request = CallLogSyncRequest(
                localLogId = localLogId,
                leadId = leadId,
                durationSeconds = durationSeconds,
                callStatus = callStatus,
                outcome = outcome,
                notes = notes,
                nextReminderTime = nextReminderTime,
                closingFormat = closingFormat,
                ptpAmount = ptpAmount
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
            Log.i("WorkRepository", "uploadRecording called: serverLogId=$serverLogId, path=$recordingPath")
            Log.i("WorkRepository", "File.exists()=${file.exists()}, File.canRead()=${file.canRead()}, length=${if (file.exists()) file.length() else -1}")

            val requestFile: okhttp3.RequestBody
            val fileName: String

            if (file.exists() && file.canRead()) {
                Log.i("WorkRepository", "Using direct file access")
                fileName = file.name
                requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            } else if (file.exists()) {
                Log.i("WorkRepository", "File exists but not readable — trying ContentResolver fallback")
                fileName = file.name
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                val projection = arrayOf(MediaStore.Audio.Media._ID)

                var foundUri: android.net.Uri? = null
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        foundUri = android.net.Uri.withAppendedPath(uri, id.toString())
                    }
                }

                if (foundUri != null) {
                    val inputStream = context.contentResolver.openInputStream(foundUri)
                    if (inputStream != null) {
                        val bytes = inputStream.readBytes()
                        inputStream.close()
                        Log.i("WorkRepository", "Read ${bytes.size} bytes via ContentResolver")
                        requestFile = bytes.toRequestBody("audio/*".toMediaTypeOrNull())
                    } else {
                        Log.e("WorkRepository", "ContentResolver returned null input stream for $foundUri")
                        return Result.failure(Exception("Cannot open recording file"))
                    }
                } else {
                    Log.e("WorkRepository", "Could not find recording in MediaStore by name: $fileName")
                    return Result.failure(Exception("Recording file not found in MediaStore"))
                }
            } else {
                Log.e("WorkRepository", "Recording file NOT FOUND at: $recordingPath")
                return Result.failure(Exception("Recording file not found"))
            }

            val body = MultipartBody.Part.createFormData("recording", fileName, requestFile)
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
            Log.e("WorkRepository", "CRITICAL ERROR during upload: ${e.message}", e)
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
