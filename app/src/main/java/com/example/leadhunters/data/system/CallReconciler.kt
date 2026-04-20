package com.example.leadhunters.data.system

import android.content.Context
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.util.AnalyticsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CallRepository,
    private val recordingScanner: RecordingScanner,
    private val analyticsHelper: AnalyticsHelper
) {

    suspend fun reconcile(phoneNumber: String, leadId: String?) {
        try {
            val allPending = repository.getAllUnreconciledLogs()
            val pendingLogs = allPending.filter { 
                PhoneNumberUtils.compare(context, phoneNumber, it.phoneNumber)
            }

            if (pendingLogs.isEmpty()) {
                analyticsHelper.logReconciliation(phoneNumber, false, "No pending logs found (Checked ${allPending.size} total)")
                return
            }
            
            Log.d("CallReconciler", "Attempting reconcile for ${pendingLogs.size} logs for $phoneNumber")
            
            for (pendingLog in pendingLogs) {
                try {
                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        "${CallLog.Calls.DATE} >= ?",
                        arrayOf((pendingLog.startTime - 60000).toString()), // 60s buffer
                        "${CallLog.Calls.DATE} DESC"
                    )

                    cursor?.use {
                        var matched = false
                        while (it.moveToNext()) {
                            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                            val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                            val systemNumberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)

                            if (durationIdx == -1 || typeIdx == -1 || dateIdx == -1 || idIdx == -1 || systemNumberIdx == -1) {
                                continue
                            }

                            val systemNumber = it.getString(systemNumberIdx)
                            if (!PhoneNumberUtils.compare(context, phoneNumber, systemNumber)) {
                                continue
                            }
                            
                            // Found a match!
                            matched = true
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
                            analyticsHelper.logReconciliation(phoneNumber, true, "Matched system ID $systemId")
                            break // Stop after first match
                        }
                        
                        if (!matched) {
                            Log.d("CallReconciler", "No matching call found in system log for $phoneNumber")
                            analyticsHelper.logReconciliation(phoneNumber, false, "No matching number in recent calls")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("CallReconciler", "Permission denied querying CallLog for $phoneNumber", e)
                    analyticsHelper.logReconciliation(phoneNumber, false, "Permission denied")
                } catch (e: Exception) {
                    Log.e("CallReconciler", "Unexpected error reconciling log ${pendingLog.id}", e)
                    analyticsHelper.logReconciliation(phoneNumber, false, e.javaClass.simpleName)
                }
            }
        } catch (e: Exception) {
            Log.e("CallReconciler", "Fatal error in reconcile loop for $phoneNumber", e)
            analyticsHelper.logReconciliation(phoneNumber, false, "Fatal error: ${e.javaClass.simpleName}")
        }
    }
}
