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
            
            Log.i("CallReconciler", "Attempting reconcile for ${pendingLogs.size} logs for $phoneNumber")
            
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

                            // 1. Scan for recording
                            Log.i("CallReconciler", "Scanning for recording of call at $date with $phoneNumber")
                            val recordingPath = try {
                                recordingScanner.findRecordingForCall(phoneNumber, date)
                            } catch (e: Exception) {
                                Log.e("CallReconciler", "Error scanning for recording", e)
                                null
                            }

                            // 2. Save duration/status
                            repository.finalizeCall(
                                callLogId = pendingLog.id,
                                duration = duration,
                                status = status,
                                systemCallLogId = systemId
                            )

                            // 3. Update with recording path IMMEDIATELY if found
                            if (recordingPath != null) {
                                Log.i("CallReconciler", "Updating log ${pendingLog.id} with recording: $recordingPath")
                                val currentLog = repository.getLogById(pendingLog.id)
                                if (currentLog != null) {
                                    repository.updateLog(currentLog.copy(recordingPath = recordingPath))
                                }
                            } else {
                                Log.w("CallReconciler", "No recording found for call with $phoneNumber at $date")
                            }

                            // 4. Update leadId if needed
                            if (pendingLog.leadId == "AD_HOC" && leadId != null) {
                                val currentLog = repository.getLogById(pendingLog.id)
                                if (currentLog != null) {
                                    repository.updateLog(currentLog.copy(leadId = leadId))
                                }
                            }

                            // 5. Finally Enqueue Sync (Recording path is now definitely in DB)
                            Log.i("CallReconciler", "Enqueuing sync for log ${pendingLog.id}")
                            repository.enqueueSync(
                                com.example.leadhunters.data.local.entities.SyncItem(
                                    type = "CALL_LOG",
                                    referenceId = pendingLog.id.toString(),
                                    operation = "CREATE",
                                    payload = "" 
                                )
                            )
                            
                            Log.i("CallReconciler", "Successfully reconciled log ${pendingLog.id} with system ID $systemId")
                            analyticsHelper.logReconciliation(phoneNumber, true, "Matched system ID $systemId")
                            break // Stop after first match
                        }
                        
                        if (!matched) {
                            Log.i("CallReconciler", "No matching call found in system log for $phoneNumber")
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
