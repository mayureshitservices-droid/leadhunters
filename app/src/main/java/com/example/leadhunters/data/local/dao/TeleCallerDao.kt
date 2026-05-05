package com.example.leadhunters.data.local.dao

import androidx.room.*
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.Reminder
import com.example.leadhunters.data.local.entities.SyncItem
import com.example.leadhunters.data.local.entities.WhatsAppTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TeleCallerDao {
    // Leads
    @Query("SELECT * FROM leads")
    fun getAllLeads(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: String): Lead?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: Lead)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<Lead>)

    @Query("DELETE FROM leads")
    suspend fun clearLeads()

    @Update
    suspend fun updateLead(lead: Lead)

    // Call Logs
    @Query("SELECT * FROM app_call_logs WHERE startTime >= :cutoffTime ORDER BY startTime DESC")
    fun getAllCallLogs(cutoffTime: Long): Flow<List<AppCallLog>>

    @Query("SELECT * FROM app_call_logs WHERE id = :id")
    suspend fun getCallLogById(id: Long): AppCallLog?

    @Query("SELECT * FROM app_call_logs WHERE phoneNumber = :number AND isReconciled = 0 ORDER BY startTime DESC")
    suspend fun getAllUnreconciledLogsForNumber(number: String): List<AppCallLog>

    @Query("SELECT * FROM app_call_logs WHERE isReconciled = 0 ORDER BY startTime DESC")
    suspend fun getAllUnreconciledLogs(): List<AppCallLog>

    @Query("SELECT * FROM app_call_logs WHERE phoneNumber = :number AND isReconciled = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestUnreconciledLogForNumber(number: String): AppCallLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: AppCallLog): Long

    @Update
    suspend fun updateCallLog(callLog: AppCallLog)

    // Outcomes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: CallOutcome): Long

    @Query("SELECT * FROM call_outcomes WHERE callLogId = :callLogId")
    suspend fun getOutcomeForCall(callLogId: Long): CallOutcome?

    @Query("SELECT * FROM call_outcomes ORDER BY timestamp DESC")
    fun getAllOutcomes(): Flow<List<CallOutcome>>

    // Reminders
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Query("SELECT * FROM reminders ORDER BY reminderTime ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    // Sync Queue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(syncItem: SyncItem)

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingSyncItems(): Flow<List<SyncItem>>

    @Update
    suspend fun updateSyncItem(syncItem: SyncItem)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED'")
    suspend fun purgeCompletedSyncItems()

    // Templates
    @Query("SELECT * FROM whatsapp_templates")
    fun getAllTemplates(): Flow<List<WhatsAppTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WhatsAppTemplate)

    @Delete
    suspend fun deleteTemplate(template: WhatsAppTemplate)

    // Analytics Dashboard Queries
    @Query("""
        SELECT 
            COUNT(*) as totalCalls,
            SUM(CASE WHEN status = 'ANSWERED' THEN 1 ELSE 0 END) as answered,
            SUM(CASE WHEN status = 'MISSED' THEN 1 ELSE 0 END) as missed,
            SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
            SUM(duration) as totalDuration
        FROM app_call_logs 
        WHERE startTime >= :startOfDay
    """)
    fun getTodayStats(startOfDay: Long): Flow<CallStats?>

    @Query("""
        SELECT 
            COUNT(*) as totalCalls,
            SUM(CASE WHEN status = 'ANSWERED' THEN 1 ELSE 0 END) as answered,
            SUM(CASE WHEN status = 'MISSED' THEN 1 ELSE 0 END) as missed,
            SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
            SUM(duration) as totalDuration
        FROM app_call_logs 
        WHERE startTime >= :startOfMonth
    """)
    fun getMonthStats(startOfMonth: Long): Flow<CallStats?>
}

data class CallStats(
    val totalCalls: Int?,
    val answered: Int?,
    val missed: Int?,
    val rejected: Int?,
    val totalDuration: Long?
)
