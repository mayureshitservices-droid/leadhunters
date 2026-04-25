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

        return if (successCount == pendingItems.size) Result.success() else Result.retry()
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
