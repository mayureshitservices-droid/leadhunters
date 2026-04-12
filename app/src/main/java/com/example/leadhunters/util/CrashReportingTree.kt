package com.example.leadhunters.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * A utility class to report logs and exceptions to Firebase Crashlytics.
 * Replaces Timber for dependency stability.
 */
object CrashReporter {
    fun log(message: String) {
        Log.d("LeadHunters", message)
        FirebaseCrashlytics.getInstance().log(message)
    }

    fun logError(t: Throwable, message: String? = null) {
        Log.e("LeadHunters", message ?: "Error occurred", t)
        message?.let { FirebaseCrashlytics.getInstance().log(it) }
        FirebaseCrashlytics.getInstance().recordException(t)
    }
}
