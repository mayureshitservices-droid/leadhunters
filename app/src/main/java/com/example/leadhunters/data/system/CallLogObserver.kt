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
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d("CallLogObserver", "Call log changed detected")
        // We don't get the specific number here easily without querying, 
        // but we trigger a check.
        onLogChanged("LOG_UPDATED")
    }
}
