package com.example.leadhunters.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.leadhunters.R
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.system.CallLogObserver
import com.example.leadhunters.data.system.CallReconciler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {

    @Inject lateinit var repository: CallRepository
    @Inject lateinit var reconciler: CallReconciler
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callLogObserver: CallLogObserver? = null
    private lateinit var telephonyManager: TelephonyManager
    private var callback: Any? = null

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "CallTrackingChannel"
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                try {
                    val notification = createNotification("Tracking call to $phoneNumber")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            startForeground(
                                NOTIFICATION_ID, 
                                notification, 
                                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                            )
                        } catch (e: Exception) {
                            Log.w("CallService", "Failed to start with phoneCall type, falling back to default: ${e.message}")
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e("CallService", "Fatal error starting foreground service", e)
                }
                registerTracking(phoneNumber)
            }
            ACTION_STOP_TRACKING -> stopSelf()
        }
        return START_STICKY
    }

    private fun registerTracking(targetNumber: String) {
        // Cleanup previous if any
        unregisterTracking()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(state, targetNumber)
                    }
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
                callback = telephonyCallback
            } else {
                val listener = @Suppress("DEPRECATION") object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChange(state, targetNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                callback = listener
            }
        } catch (e: SecurityException) {
            Log.e("CallService", "Missing READ_PHONE_STATE permission.", e)
        }

        callLogObserver = CallLogObserver(this) { 
            serviceScope.launch {
                reconciler.reconcile(targetNumber)
            }
        }
        callLogObserver?.register()
    }

    private fun unregisterTracking() {
        callLogObserver?.unregister()
        callLogObserver = null

        callback?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it is TelephonyCallback) {
                telephonyManager.unregisterTelephonyCallback(it)
            } else if (it is PhoneStateListener) {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
        callback = null
    }

    private fun handleCallStateChange(state: Int, targetNumber: String) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallService", "Call IDLE")
                serviceScope.launch {
                    delay(2000) 
                    reconciler.reconcile(targetNumber)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("CallService", "Call OFFHOOK (Active)")
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallService", "Call RINGING")
            }
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TeleCalling Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterTracking()
        serviceScope.cancel()
        super.onDestroy()
    }
}
