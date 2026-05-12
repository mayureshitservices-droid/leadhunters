package com.example.leadhunters.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import com.example.leadhunters.data.remote.api.AppApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class AppUpdater @Inject constructor(
    private val appApiService: AppApiService,
    @ApplicationContext private val context: Context
) {
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersionCode = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            return@withContext UpdateResult.Error("Failed to get local version: ${e.message}")
        }

        try {
            android.util.Log.d("AppUpdater", "Checking for update... Current version: $currentVersionCode")
            val response = appApiService.getAppVersion()
            
            if (response.isSuccessful) {
                val versionInfo = response.body()
                if (versionInfo == null) {
                    android.util.Log.e("AppUpdater", "Update check failed: Response body is null")
                    return@withContext UpdateResult.Error("Server returned empty version info")
                }

                android.util.Log.d("AppUpdater", "Server version: ${versionInfo.versionCode} (${versionInfo.versionName})")
                
                if (versionInfo.versionCode > currentVersionCode) {
                    android.util.Log.i("AppUpdater", "Update available! Mandatory: ${versionInfo.mandatory}")
                    return@withContext UpdateResult.UpdateAvailable(
                        versionCode = versionInfo.versionCode,
                        versionName = versionInfo.versionName,
                        downloadUrl = versionInfo.downloadUrl,
                        mandatory = versionInfo.mandatory
                    )
                } else {
                    android.util.Log.d("AppUpdater", "App is up to date")
                    return@withContext UpdateResult.NoUpdate
                }
            } else {
                val errorMsg = "Server error: ${response.code()} ${response.message()}"
                android.util.Log.e("AppUpdater", errorMsg)
                return@withContext UpdateResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AppUpdater", "Connection error: ${e.message}")
            return@withContext UpdateResult.Error("Connection error: ${e.message ?: "Unknown"}")
        }
    }

    private val _downloadProgress = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    fun downloadAndInstall(context: Context, url: String, fileName: String = "app-update.apk") {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading LeadHunters Update")
            setDescription("Downloading latest version...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setMimeType("application/vnd.android.package-archive")
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        
        // Start monitoring progress
        _downloadProgress.value = 0
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        
                        if (statusIdx != -1) {
                            val status = cursor.getInt(statusIdx)
                            val bytesTotal = if (totalIdx != -1) cursor.getInt(totalIdx) else 0

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloading = false
                                _downloadProgress.value = 100
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                downloading = false
                                _downloadProgress.value = -1 // Signal failure
                                android.util.Log.e("AppUpdater", "Download failed with status: $status")
                            } else if (bytesTotal > 0 && downloadedIdx != -1) {
                                val bytesDownloaded = cursor.getInt(downloadedIdx)
                                val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                                _downloadProgress.value = progress
                            }
                        }
                    } else {
                        // Cursor is empty - something went wrong with the download ID
                        downloading = false
                        _downloadProgress.value = -1
                    }
                } ?: run {
                    // Query returned null
                    downloading = false
                    _downloadProgress.value = -1
                }
                delay(1000)
            }
        }
    }
}

sealed class UpdateResult {
    data class UpdateAvailable(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val mandatory: Boolean
    ) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
