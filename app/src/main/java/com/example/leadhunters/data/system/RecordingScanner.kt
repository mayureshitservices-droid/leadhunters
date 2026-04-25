package com.example.leadhunters.data.system

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val oemPaths = listOf(
        "/Recordings/Call/",             // Samsung/Standard
        "/Recordings/sound_recorder/call_rec/", // New Xiaomi path
        "/MIUI/sound_recorder/call_rec/", // Older Xiaomi path
        "/Download/",                   // Some older apps save here
        "/Record/Call/",                 // Realme/OPPO
        "/Music/Recordings/Call/",       // Alternative Realme/OPPO/OnePlus
        "/CallRecord/",
        "/Recorder/Call/",
        "/Sounds/CallRecord/"
    )

    fun findRecordingForCall(phoneNumber: String, callStartTime: Long): String? {
        // Method 1: Raw File Scan (OEM specific)
        val rawPath = findByRawPaths(phoneNumber, callStartTime)
        if (rawPath != null) return rawPath

        // Method 2: MediaStore Scan (System standard fallback)
        return findByMediaStore(phoneNumber, callStartTime)
    }

    private fun findByRawPaths(phoneNumber: String, callStartTime: Long): String? {
        val rootPath = try {
            // Priority 1: Official System property for root
            System.getenv("EXTERNAL_STORAGE") ?: "/storage/emulated/0"
        } catch (e: Exception) {
            "/storage/emulated/0"
        }
        val root = File(rootPath)
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        for (path in oemPaths) {
            val dir = File(root, path)
            Log.i("RecordingScanner", "Checking directory: ${dir.absolutePath} (Exists: ${dir.exists()})")
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    val name = file.name.lowercase(Locale.ROOT)
                    file.isFile && (name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a") || name.endsWith(".wav"))
                }
                
                Log.i("RecordingScanner", "Found ${files?.size ?: 0} audio files in ${dir.name}")
                files?.sortByDescending { it.lastModified() }
                
                val found = files?.find { file ->
                    val fileTime = file.lastModified()
                    val timeDiff = Math.abs(fileTime - callStartTime)
                    val isRecent = timeDiff < 10 * 60 * 1000 // 10 min window
                    
                    val fileNameLower = file.name.lowercase(Locale.ROOT)
                    val containsNumber = fileNameLower.contains(normalizedNumber) || 
                                         (normalizedNumber.length > 5 && fileNameLower.contains(normalizedNumber.takeLast(5)))

                    Log.i("RecordingScanner", "Evaluating file: ${file.name} | TimeDiff: ${timeDiff/1000}s | MatchNumber: $containsNumber")
                    isRecent && containsNumber
                }
                
                if (found != null) {
                    Log.i("RecordingScanner", "MATCH FOUND: ${found.absolutePath}")
                    return found.absolutePath
                }
            }
        }
        Log.w("RecordingScanner", "No recording found in raw paths for $phoneNumber")
        return null
    }

    private fun findByMediaStore(phoneNumber: String, callStartTime: Long): String? {
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.DATE_ADDED,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf((callStartTime / 1000 - 600).toString()) // 10 min before
        val sortOrder = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                val nameIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIdx)
                    val name = cursor.getString(nameIdx).lowercase(Locale.ROOT)
                    
                    if (name.contains(normalizedNumber) || 
                        (normalizedNumber.length > 5 && name.contains(normalizedNumber.takeLast(5)))) {
                        Log.i("RecordingScanner", "MediaStore found recording: $path")
                        return path
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingScanner", "MediaStore query failed: ${e.message}")
        }
        return null
    }
}
