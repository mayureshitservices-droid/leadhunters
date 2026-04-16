package com.example.leadhunters.data.system

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.example.leadhunters.data.repository.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CallRepository,
    private val recordingScanner: RecordingScanner
) {

    suspend fun reconcile(phoneNumber: String, leadId: String?) {
        try {
            val pendingLogs = repository.getUnreconciledLogsForNumber(phoneNumber)
            if (pendingLogs.isEmpty()) return
            
            Log.d("CallReconciler", "Attempting reconcile for ${pendingLogs.size} logs for $phoneNumber")
            
            for (pendingLog in pendingLogs) {
                try {
                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} >= ?",
                        arrayOf(phoneNumber, (pendingLog.startTime - 60000).toString()), // 60s buffer for better safety
                        "${CallLog.Calls.DATE} DESC"
                    )

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                            val idIdx = it.getColumnIndex(CallLog.Calls._ID)

                            if (durationIdx == -1 || typeIdx == -1 || dateIdx == -1 || idIdx == -1) {
                                Log.w("CallReconciler", "Missing required CallLog columns")
                                return@use
                            }

                            val duration = it.getLong(durationIdx)
                            val type = it.getInt(typeIdx)
                            val date = it.getLong(dateIdx)
                            val systemId = it.getLong(idIdx)

                            val status = when (type) {
                                CallLog.Calls.OUTGOING_TYPE -> if (duration > 0L) "ANSWERED" else "REJECTED"
                                CallLog.Calls.INCOMING_TYPE -> if (duration > 0L) "ANSWERED" else "MISSED"
                                CallLog.Calls.MISSED_TYPE -> "MISSED"
                                CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                                else -> "ENDED"
                            }

                            // Scan for recording
                            val recordingPath = try {
                                recordingScanner.findRecordingForCall(phoneNumber, date)
                            } catch (e: Exception) {
                                Log.e("CallReconciler", "Error scanning for recording", e)
                                null
                            }

                            repository.finalizeCall(
                                callLogId = pendingLog.id,
                                duration = duration,
                                status = status,
                                systemCallLogId = systemId
                            )

                            // If we have a leadId from context but not in the log, update it
                            if (pendingLog.leadId == "AD_HOC" && leadId != null) {
                                val currentLog = repository.getLogById(pendingLog.id)
                                if (currentLog != null) {
                                    repository.updateLog(currentLog.copy(leadId = leadId))
                                }
                            }

                            // Enqueue Sync
                            repository.enqueueSync(
                                com.example.leadhunters.data.local.entities.SyncItem(
                                    type = "CALL_LOG",
                                    referenceId = pendingLog.id.toString(),
                                    operation = "CREATE",
                                    payload = "" // SyncWorker will fetch the data
                                )
                            )
                            
                            // Update with recording if found
                            if (recordingPath != null) {
                                val updatedLog = repository.getLogById(pendingLog.id)
                                if (updatedLog != null) {
                                    repository.updateLog(updatedLog.copy(recordingPath = recordingPath))
                                }
                            }
                            
                            Log.d("CallReconciler", "Reconciled log ${pendingLog.id} with system ID $systemId")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("CallReconciler", "Permission denied querying CallLog for $phoneNumber", e)
                } catch (e: Exception) {
                    Log.e("CallReconciler", "Unexpected error reconciling log ${pendingLog.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CallReconciler", "Fatal error in reconcile loop for $phoneNumber", e)
        }
    }
}
