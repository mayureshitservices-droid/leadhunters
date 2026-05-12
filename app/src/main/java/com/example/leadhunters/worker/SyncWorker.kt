package com.example.leadhunters.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.SyncItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val teleCallerDao: TeleCallerDao,
    private val workRepository: com.example.leadhunters.data.repository.WorkRepository,
    private val analyticsHelper: com.example.leadhunters.util.AnalyticsHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Now also checking for FAILED items that haven't reached max retries
        val pendingItems = teleCallerDao.getPendingSyncItems().first()
            .filter { it.status == "PENDING" || (it.status == "FAILED" && it.retryCount < 5) }
        
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

        if (successCount > 0) {
            teleCallerDao.purgeCompletedSyncItems()
            // Run cleanup for old recordings
            performRetentionCleanup()
        }

        return if (successCount == pendingItems.size) Result.success() else Result.retry()
    }

    private suspend fun performRetentionCleanup() {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val oldLogs = teleCallerDao.getLogsWithRecordingsOlderThan(sevenDaysAgo)
            
            if (oldLogs.isEmpty()) return
            
            Log.i("SyncWorker", "Starting retention cleanup for ${oldLogs.size} recordings...")
            
            var deletedCount = 0
            for (log in oldLogs) {
                val path = log.recordingPath ?: continue
                val file = java.io.File(path)
                
                if (file.exists()) {
                    if (file.delete()) {
                        deletedCount++
                        // Clear path in DB so UI knows it's gone
                        teleCallerDao.updateCallLog(log.copy(recordingPath = null))
                    }
                } else {
                    // File already gone, just clear the path
                    teleCallerDao.updateCallLog(log.copy(recordingPath = null))
                }
            }
            Log.i("SyncWorker", "Retention cleanup finished. Deleted $deletedCount files.")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during retention cleanup: ${e.message}")
        }
    }

    private fun showErrorNotification(title: String, message: String) {
        val channelId = "sync_errors"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sync Errors", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private suspend fun processSyncItem(item: SyncItem): Boolean {
        return when (item.type) {
            "CALL_LOG" -> {
                val callLog = teleCallerDao.getCallLogById(item.referenceId.toLong())
                if (callLog != null) {
                    val outcome = teleCallerDao.getOutcomeForCall(callLog.id)
                    val syncResult = workRepository.syncCallLog(
                        localLogId = "${callLog.id}_${callLog.startTime}",
                        leadId = callLog.leadId,
                        durationSeconds = callLog.duration?.toInt() ?: 0,
                        callStatus = callLog.status,
                        outcome = outcome?.outcomeType,
                        notes = outcome?.remarks
                    )
                    
                    if (syncResult.isSuccess) {
                        val serverLogId = syncResult.getOrNull()
                        Log.i("SyncWorker", "Call log metadata synced. Server ID: $serverLogId")
                        
                        if (serverLogId != null && !callLog.recordingPath.isNullOrEmpty()) {
                            // Safety: Wait and retry for file to be flushed to storage (up to 3 attempts)
                            var recordingFile = java.io.File(callLog.recordingPath)
                            var attempts = 0
                            while (!recordingFile.exists() && attempts < 3) {
                                delay(2000) // Wait 2s between checks
                                recordingFile = java.io.File(callLog.recordingPath)
                                attempts++
                            }
                            
                            if (recordingFile.exists()) {
                                val recordingResult = workRepository.uploadRecording(
                                    serverLogId = serverLogId,
                                    recordingPath = callLog.recordingPath
                                )
                                if (!recordingResult.isSuccess) {
                                    val error = recordingResult.exceptionOrNull()?.message ?: "Unknown Error"
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(applicationContext, "Upload Failed: $error", Toast.LENGTH_LONG).show()
                                    }
                                    showErrorNotification("Recording Upload Failed", error)
                                    Log.e("SyncWorker", "Recording upload FAILED for server log $serverLogId: $error")
                                } else {
                                    Log.i("SyncWorker", "Recording upload SUCCESS for server log $serverLogId")
                                }
                                recordingResult.isSuccess
                            } else {
                                Log.e("SyncWorker", "Recording file NOT FOUND after retries at: ${callLog.recordingPath}")
                                showErrorNotification("Recording Not Found", "Could not locate recording file after multiple attempts.")
                                true // Consider it "synced" to avoid infinite retry of a non-existent file
                            }
                        } else {
                            if (serverLogId == null) Log.w("SyncWorker", "Sync succeeded but no server ID returned!")
                            true 
                        }
                    } else {
                        val error = syncResult.exceptionOrNull()?.message ?: "Unknown Error"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Sync Failed: $error", Toast.LENGTH_LONG).show()
                        }
                        showErrorNotification("Call Sync Failed", error)
                        Log.e("SyncWorker", "Call log metadata sync FAILED: $error")
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
