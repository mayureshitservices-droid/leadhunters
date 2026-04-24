package com.example.leadhunters.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.SyncItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val teleCallerDao: TeleCallerDao,
    private val workRepository: com.example.leadhunters.data.repository.WorkRepository,
    private val analyticsHelper: com.example.leadhunters.util.AnalyticsHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingItems = teleCallerDao.getPendingSyncItems().first()
        
        if (pendingItems.isEmpty()) return Result.success()

        analyticsHelper.logSyncWorkerTriggered(pendingItems.size)

        var successCount = 0
        for (item in pendingItems) {
            val result = processSyncItem(item)
            if (result) {
                teleCallerDao.updateSyncItem(item.copy(status = "COMPLETED"))
                successCount++
            } else {
                teleCallerDao.updateSyncItem(item.copy(
                    status = "FAILED",
                    retryCount = item.retryCount + 1
                ))
            }
        }

        return if (successCount == pendingItems.size) Result.success() else Result.retry()
    }

    private suspend fun processSyncItem(item: SyncItem): Boolean {
        return when (item.type) {
            "CALL_LOG" -> {
                val callLog = teleCallerDao.getCallLogById(item.referenceId.toLong())
                if (callLog != null) {
                    val outcome = teleCallerDao.getOutcomeForCall(callLog.id)
                    val syncResult = workRepository.syncCallLog(
                        localLogId = callLog.id,
                        leadId = callLog.leadId,
                        durationSeconds = callLog.duration?.toInt() ?: 0,
                        callStatus = callLog.status,
                        outcome = outcome?.outcomeType,
                        notes = outcome?.remarks
                    )
                    
                    if (syncResult.isSuccess) {
                        val serverLogId = syncResult.getOrNull()
                        if (serverLogId != null && !callLog.recordingPath.isNullOrEmpty()) {
                            // If we have a recording and a server ID, upload it
                            val recordingResult = workRepository.uploadRecording(
                                serverLogId = serverLogId,
                                recordingPath = callLog.recordingPath
                            )
                            recordingResult.isSuccess
                        } else {
                            true // No recording or no server ID (but sync was success)
                        }
                    } else {
                        false
                    }
                } else {
                    true // Item gone, consider it "synced" to clear queue
                }
            }
            else -> true
        }
    }
}
