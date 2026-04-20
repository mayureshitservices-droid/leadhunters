package com.example.leadhunters.util

import android.content.Context
import android.os.Bundle
import com.example.leadhunters.data.local.entities.Lead
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun logCallStarted(leadId: String, phoneNumber: String) {
        val params = Bundle().apply {
            putString("lead_id", leadId)
            putString("phone_number", phoneNumber)
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("sync_call_started", params)
    }

    fun logReconciliation(phoneNumber: String, success: Boolean, reason: String) {
        val params = Bundle().apply {
            putString("phone_number", phoneNumber)
            putBoolean("success", success)
            putString("reason", reason)
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("sync_call_reconcile", params)
    }

    fun logSyncWorkerTriggered(queueSize: Int) {
        val params = Bundle().apply {
            putInt("queue_size", queueSize)
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("sync_worker_triggered", params)
    }

    fun logApiSyncResult(leadId: String, httpCode: Int, success: Boolean, errorMessage: String?) {
        val params = Bundle().apply {
            putString("lead_id", leadId)
            putInt("http_code", httpCode)
            putBoolean("success", success)
            errorMessage?.let { putString("error_message", it) }
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("sync_api_result", params)
    }

    // Diagnostic: confirms the pending DB row was actually written
    fun logCallPendingCreated(rowId: Long, phoneNumber: String, success: Boolean, error: String? = null) {
        val params = Bundle().apply {
            putLong("row_id", rowId)
            putString("phone_number", phoneNumber)
            putBoolean("success", success)
            error?.let { putString("error", it) }
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("sync_db_write", params)
    }
}
