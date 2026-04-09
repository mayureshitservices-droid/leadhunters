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
    private val teleCallerDao: TeleCallerDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingItems = teleCallerDao.getPendingSyncItems().first()
        
        if (pendingItems.isEmpty()) return Result.success()

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
        // Here we would call the "Pluggable" Sync Action
        // For now, it's a placeholder returning true
        return true
    }
}
