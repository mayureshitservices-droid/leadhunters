package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.remote.api.WorkApiService
import com.example.leadhunters.data.remote.model.CallLogSyncRequest
import com.example.leadhunters.util.AnalyticsHelper
import kotlinx.coroutines.flow.Flow
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
                teleCallerDao.clearLeads()
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
        leadId: String,
        durationSeconds: Int,
        status: String,
        notes: String?
    ): Result<Unit> {
        return try {
            val request = CallLogSyncRequest(
                leadId = leadId,
                durationSeconds = durationSeconds,
                status = status,
                notes = notes
            )
            val response = apiService.syncCallLog(request)
            if (response.isSuccessful) {
                analyticsHelper.logApiSyncResult(leadId, response.code(), true, null)
                Result.success(Unit)
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
}
