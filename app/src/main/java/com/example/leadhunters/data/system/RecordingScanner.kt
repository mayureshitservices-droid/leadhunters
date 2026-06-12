package com.example.leadhunters.data.system

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val candidatePaths = ConcurrentHashMap.newKeySet<String>()

    fun addCandidatePath(path: String) {
        candidatePaths.add(path)
    }

    fun clearCandidatePaths() {
        candidatePaths.clear()
    }

    suspend fun findRecordingForCall(phoneNumber: String, callStartTime: Long): String? {
        val candidate = checkCandidatePaths(phoneNumber, callStartTime)
        if (candidate != null) return candidate

        val retryDelays = listOf(0L, 5000L, 15000L, 30000L)
        for (attempt in retryDelays.indices) {
            if (retryDelays[attempt] > 0) {
                Log.i("RecordingScanner", "Retry ${attempt + 1}/${retryDelays.size} in ${retryDelays[attempt]}ms")
                delay(retryDelays[attempt])
            }
            val path = scanAll(phoneNumber, callStartTime)
            if (path != null) {
                Log.i("RecordingScanner", "Recording found on attempt ${attempt + 1}: $path")
                return path
            }
        }
        Log.w("RecordingScanner", "Recording not found after ${retryDelays.size} attempts for $phoneNumber")
        return null
    }

    private fun checkCandidatePaths(phoneNumber: String, callStartTime: Long): String? {
        val candidates = candidatePaths.toList()
        if (candidates.isEmpty()) return null

        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        if (normalizedNumber.isBlank()) return null
        Log.i("RecordingScanner", "Checking ${candidates.size} FileObserver-discovered paths first")

        candidates.forEach { path ->
            val file = File(path)
            if (!file.exists()) return@forEach
            val fileNameLower = file.name.lowercase(Locale.ROOT)
            val timeDiff = Math.abs(file.lastModified() - callStartTime)
            val isRecent = timeDiff < 15 * 60 * 1000
            val containsNumber = fileNameLower.contains(normalizedNumber) ||
                (normalizedNumber.length > 5 && fileNameLower.contains(normalizedNumber.takeLast(5)))
            if (isRecent && containsNumber) {
                Log.i("RecordingScanner", "Candidate number match: $path")
                return path
            }
        }

        candidates.forEach { path ->
            val file = File(path)
            if (!file.exists()) return@forEach
            val timeDiff = Math.abs(file.lastModified() - callStartTime)
            if (timeDiff < 60_000L) {
                Log.i("RecordingScanner", "Candidate time fallback match: $path (${timeDiff}ms)")
                return path
            }
        }

        return null
    }

    private fun scanAll(phoneNumber: String, callStartTime: Long): String? {
        val mediaPath = findByMediaStore(phoneNumber, callStartTime)
        if (mediaPath != null) return mediaPath

        if (hasManageStoragePermission()) {
            val rawPath = findByRawPaths(phoneNumber, callStartTime)
            if (rawPath != null) return rawPath
        }

        return null
    }

    private fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun findByRawPaths(phoneNumber: String, callStartTime: Long): String? {
        val rootPath = resolveStorageRoot()
        val root = File(rootPath)
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        if (normalizedNumber.isBlank()) return null

        for (path in OEM_PATHS) {
            val dir = File(root, path)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    val name = file.name.lowercase(Locale.ROOT)
                    file.isFile && (name.endsWith(".mp3") || name.endsWith(".aac") ||
                                   name.endsWith(".m4a") || name.endsWith(".wav") ||
                                   name.endsWith(".amr") || name.endsWith(".3gp"))
                }

                files?.sortByDescending { it.lastModified() }

                // First pass: try number-based matching (existing behavior)
                val foundByNumber = files?.find { file ->
                    val fileTime = file.lastModified()
                    val timeDiff = Math.abs(fileTime - callStartTime)
                    val isRecent = timeDiff < 15 * 60 * 1000

                    val fileNameLower = file.name.lowercase(Locale.ROOT)
                    val containsNumber = fileNameLower.contains(normalizedNumber) ||
                                         (normalizedNumber.length > 5 && fileNameLower.contains(normalizedNumber.takeLast(5)))

                    isRecent && containsNumber
                }

                if (foundByNumber != null) {
                    return foundByNumber.absolutePath
                }

                // Second pass: time-based fallback for recordings that don't include the number in the filename
                val foundByTime = files?.find { file ->
                    val timeDiff = Math.abs(file.lastModified() - callStartTime)
                    timeDiff < 60_000L
                }

                if (foundByTime != null) {
                    Log.i("RecordingScanner", "Time fallback match: ${foundByTime.name} (${Math.abs(foundByTime.lastModified() - callStartTime)}ms from call)")
                    return foundByTime.absolutePath
                }
            }
        }
        return null
    }

    private fun findByMediaStore(phoneNumber: String, callStartTime: Long): String? {
        val normalizedNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        if (normalizedNumber.isBlank()) return null

        val windowStart = (callStartTime / 1000) - 900
        val windowEnd = (callStartTime / 1000) + 900

        val selection = "${MediaStore.Audio.Media.DATE_ADDED} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(windowStart.toString(), windowEnd.toString())
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val projection = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                var fallbackPath: String? = null
                var fallbackTimeDiff = Long.MAX_VALUE

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.lowercase(Locale.ROOT) ?: ""
                    val path = cursor.getString(pathIdx) ?: continue
                    val dateAdded = cursor.getLong(dateIdx)

                    val containsNumber = name.contains(normalizedNumber) ||
                                         (normalizedNumber.length > 5 && name.contains(normalizedNumber.takeLast(5)))

                    if (containsNumber) {
                        Log.i("RecordingScanner", "MediaStore match: $name (added: $dateAdded, path: $path)")
                        return path
                    }

                    // Track closest file by time as fallback
                    val addedMillis = dateAdded * 1000L
                    val timeDiff = Math.abs(addedMillis - callStartTime)
                    if (timeDiff < 60_000L && timeDiff < fallbackTimeDiff) {
                        fallbackPath = path
                        fallbackTimeDiff = timeDiff
                    }
                }

                if (fallbackPath != null) {
                    Log.i("RecordingScanner", "MediaStore time fallback match: $fallbackPath (${fallbackTimeDiff}ms from call)")
                    return fallbackPath
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingScanner", "MediaStore query failed: ${e.message}")
        }
        return null
    }

    companion object {
        val OEM_PATHS = listOf(
            "/Call/",
            "/Recordings/Call/",
            "/Recordings/sound_recorder/call_rec/",
            "/MIUI/sound_recorder/call_rec/",
            "/Download/",
            "/Record/Call/",
            "/Music/Recordings/Call/",
            "/CallRecord/",
            "/Recorder/Call/",
            "/Sounds/CallRecord/"
        )

        fun resolveStorageRoot(): String {
            return try {
                System.getenv("EXTERNAL_STORAGE") ?: "/storage/emulated/0"
            } catch (e: Exception) {
                "/storage/emulated/0"
            }
        }

        fun getAbsoluteOemPaths(): List<String> {
            val root = resolveStorageRoot()
            return OEM_PATHS.map { "$root$it" }
        }
    }
}
