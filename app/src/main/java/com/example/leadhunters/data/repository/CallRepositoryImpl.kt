package com.example.leadhunters.data.repository

import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.Lead
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CallRepositoryImpl @Inject constructor(
    private val teleCallerDao: TeleCallerDao
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
        return teleCallerDao.insertCallLog(log)
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

    override suspend fun insertOutcome(outcome: com.example.leadhunters.data.local.entities.CallOutcome) {
        teleCallerDao.insertOutcome(outcome)
    }
}
