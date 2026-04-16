package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.Reminder
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun getLeads(): Flow<List<Lead>>
    suspend fun insertLead(lead: Lead)
    fun getCallLogs(): Flow<List<AppCallLog>>
    suspend fun startCall(leadId: String?, phoneNumber: String): Long
    suspend fun finalizeCall(callLogId: Long, duration: Long, status: String, systemCallLogId: Long? = null)
    suspend fun getLogById(id: Long): AppCallLog?
    suspend fun updateLog(callLog: AppCallLog)
    suspend fun getLatestUnreconciled(number: String): AppCallLog?
    suspend fun getUnreconciledLogsForNumber(number: String): List<AppCallLog>
    suspend fun insertOutcome(outcome: com.example.leadhunters.data.local.entities.CallOutcome): Long
    
    // Reminders
    fun getReminders(): Flow<List<Reminder>>
    suspend fun insertReminder(reminder: Reminder): Long

    // Sync
    suspend fun enqueueSync(item: com.example.leadhunters.data.local.entities.SyncItem)
}
