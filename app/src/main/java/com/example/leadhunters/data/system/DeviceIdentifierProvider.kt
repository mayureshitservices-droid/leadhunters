package com.example.leadhunters.data.system

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentifierProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Retrieves the Android ID, which is a 64-bit number (as a hex string) 
     * that is randomly generated when the user first sets up the device 
     * and should remain constant for the lifetime of the user's device.
     */
    fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    /**
     * Retrieves the human-readable device name (Model).
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }
}
