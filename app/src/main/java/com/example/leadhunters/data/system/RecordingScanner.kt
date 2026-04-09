package com.example.leadhunters.data.system

import android.os.Environment
import android.util.Log
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingScanner @Inject constructor() {

    private val oemPaths = listOf(
        "/MIUI/sound_recorder/call_rec/", // Xiaomi
        "/Recordings/Call/",             // Samsung
        "/Record/Call/",                 // Realme/OPPO
        "/Music/Recordings/Call/",       // Alternative Realme/OPPO/OnePlus
        "/CallRecord/",
        "/Recorder/Call/"
    )

    fun findRecordingForCall(phoneNumber: String, callStartTime: Long): String? {
        val root = Environment.getExternalStorageDirectory()
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        for (path in oemPaths) {
            val dir = File(root, path)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    val name = file.name.lowercase(Locale.ROOT)
                    file.isFile && (name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a") || name.endsWith(".wav"))
                }
                
                // Sort by last modified descending to get newest first
                files?.sortByDescending { it.lastModified() }
                
                val found = files?.find { file ->
                    val fileTime = file.lastModified()
                    // Check if file was modified within 5 minutes of call start
                    val timeDiff = Math.abs(fileTime - callStartTime)
                    val isRecent = timeDiff < 5 * 60 * 1000 
                    
                    val fileNameLower = file.name.lowercase(Locale.ROOT)
                    val containsNumber = fileNameLower.contains(normalizedNumber) || 
                                         (normalizedNumber.length > 5 && fileNameLower.contains(normalizedNumber.takeLast(5)))

                    isRecent && containsNumber
                }
                
                if (found != null) {
                    Log.d("RecordingScanner", "Found recording: ${found.absolutePath}")
                    return found.absolutePath
                }
            }
        }
        
        return null
    }
}
