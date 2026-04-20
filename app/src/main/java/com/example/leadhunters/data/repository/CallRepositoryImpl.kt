package com.example.leadhunters.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.Reminder
import com.example.leadhunters.worker.SyncWorker
import com.example.leadhunters.util.AnalyticsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CallRepositoryImpl @Inject constructor(
    private val teleCallerDao: TeleCallerDao,
    @ApplicationContext private val context: Context,
    private val analyticsHelper: AnalyticsHelper
) : CallRepository {

    override fun getLeads(): Flow<List<Lead>> = teleCallerDao.getAllLeads()

    override suspend fun insertLead(lead: Lead) {
        teleCallerDao.insertLead(lead)
    }

    override fun getCallLogs(): Flow<List<AppCallLog>> = teleCallerDao.getAllCallLogs()

    override suspend fun startCall(leadId: String?, phoneNumber: String): Long {
        val log = AppCallLog(
            leadId = leadId ?: "AD_HOC",
            phoneNumber = phoneNumber,
            startTime = System.currentTimeMillis(),
            type = "OUTGOING",
            status = "PENDING"
        )
        return try {
            val rowId = teleCallerDao.insertCallLog(log)
            analyticsHelper.logCallPendingCreated(rowId, phoneNumber, true)
            rowId
        } catch (e: Exception) {
            analyticsHelper.logCallPendingCreated(-1L, phoneNumber, false, e.message)
            throw e
        }
    }

    override suspend fun finalizeCall(callLogId: Long, duration: Long, status: String, systemCallLogId: Long?) {
        val log = teleCallerDao.getCallLogById(callLogId)
        if (log != null) {
            teleCallerDao.updateCallLog(log.copy(
                endTime = System.currentTimeMillis(),
                duration = duration,
                status = status,
                systemCallLogId = systemCallLogId,
                isReconciled = true
            ))
        }
    }

    override suspend fun getLogById(id: Long): AppCallLog? = teleCallerDao.getCallLogById(id)

    override suspend fun updateLog(callLog: AppCallLog) {
        teleCallerDao.updateCallLog(callLog)
    }

    override suspend fun getLatestUnreconciled(number: String): AppCallLog? {
        return teleCallerDao.getLatestUnreconciledLogForNumber(number)
    }

    override suspend fun getUnreconciledLogsForNumber(number: String): List<AppCallLog> {
        return teleCallerDao.getAllUnreconciledLogsForNumber(number)
    }

    override suspend fun getAllUnreconciledLogs(): List<AppCallLog> {
        return teleCallerDao.getAllUnreconciledLogs()
    }

    override suspend fun insertOutcome(outcome: com.example.leadhunters.data.local.entities.CallOutcome): Long {
        return teleCallerDao.insertOutcome(outcome)
    }

    override fun getReminders(): Flow<List<Reminder>> = teleCallerDao.getAllReminders()

    override suspend fun insertReminder(reminder: Reminder): Long {
        return teleCallerDao.insertReminder(reminder)
    }

    private fun triggerSyncWorker() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "CallSyncWorker",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    override suspend fun enqueueSync(item: com.example.leadhunters.data.local.entities.SyncItem) {
        teleCallerDao.insertSyncItem(item)
        triggerSyncWorker()
    }
}
