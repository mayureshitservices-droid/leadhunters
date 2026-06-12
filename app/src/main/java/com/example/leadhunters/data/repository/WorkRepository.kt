package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.remote.model.CampaignDto
import kotlinx.coroutines.flow.Flow

interface WorkRepository {
    fun getLeads(): Flow<List<Lead>>
    fun getCampaigns(): Flow<Result<List<CampaignDto>>>
    suspend fun claimCampaign(campaignName: String): Result<Int>
    suspend fun syncCallLog(
        localLogId: String,
        leadId: String,
        durationSeconds: Int,
        callStatus: String,
        outcome: String? = null,
        notes: String? = null,
        nextReminderTime: Long? = null
    ): Result<String?> // Return server log ID

    suspend fun uploadRecording(
        serverLogId: String,
        recordingPath: String
    ): Result<Unit>
    suspend fun deleteLeadsLocally(ids: List<String>)
    suspend fun updateTelecallerStatus(status: String)
}
