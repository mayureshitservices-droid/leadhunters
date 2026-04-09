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

    suspend fun reconcile(phoneNumber: String) {
        val pendingLog = repository.getLatestUnreconciled(phoneNumber) ?: return
        
        Log.d("CallReconciler", "Attempting reconcile for ${pendingLog.phoneNumber}")
        
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} >= ?",
            arrayOf(phoneNumber, (pendingLog.startTime - 10000).toString()), // 10s buffer
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val systemId = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls._ID))

                val status = when (type) {
                    CallLog.Calls.OUTGOING_TYPE -> if (duration > 0L) "ANSWERED" else "REJECTED"
                    CallLog.Calls.INCOMING_TYPE -> if (duration > 0L) "ANSWERED" else "MISSED"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    else -> "ENDED"
                }

                // Scan for recording
                val recordingPath = recordingScanner.findRecordingForCall(phoneNumber, date)

                repository.finalizeCall(
                    callLogId = pendingLog.id,
                    duration = duration,
                    status = status,
                    systemCallLogId = systemId
                )
                
                // Update with recording if found
                val updatedLog = repository.getLogById(pendingLog.id)
                if (updatedLog != null) {
                    repository.updateLog(updatedLog.copy(recordingPath = recordingPath))
                }
                
                Log.d("CallReconciler", "Reconciled log ${pendingLog.id} with system ID $systemId")
            }
        }
    }
}
