package com.example.leadhunters.data.system

import android.content.Context
import android.database.ContentObserver
import android.provider.CallLog
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallLogObserver(
    private val context: Context,
    private val onLogChanged: (String) -> Unit
) : ContentObserver(null) {

    fun register() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.READ_CALL_LOG
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                context.contentResolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    this
                )
            } else {
                Log.w("CallLogObserver", "Cannot register: READ_CALL_LOG permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e("CallLogObserver", "SecurityException while registering observer", e)
        } catch (e: Exception) {
            Log.e("CallLogObserver", "Unexpected error registering observer", e)
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
        } catch (e: Exception) {
            Log.e("CallLogObserver", "Error unregistering observer", e)
        }
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d("CallLogObserver", "Call log changed detected")
        // We don't get the specific number here easily without querying, 
        // but we trigger a check.
        onLogChanged("LOG_UPDATED")
    }
}
