package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.entities.Lead
import kotlinx.coroutines.flow.Flow

interface WorkRepository {
    fun getLeads(): Flow<List<Lead>>
    suspend fun syncLeads(): Result<Unit>
    suspend fun syncCallLog(
        localLogId: Long,
        leadId: String,
        durationSeconds: Int,
        callStatus: String,
        outcome: String? = null,
        notes: String? = null
    ): Result<Unit>
}
