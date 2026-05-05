package com.example.leadhunters.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import com.example.leadhunters.data.remote.api.AppApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AppUpdater @Inject constructor(
    private val appApiService: AppApiService,
    @ApplicationContext private val context: Context
) {
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val response = appApiService.getAppVersion()
            if (response.isSuccessful) {
                val versionInfo = response.body()
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                if (versionInfo != null && versionInfo.versionCode > currentVersionCode) {
                    return@withContext UpdateResult.UpdateAvailable(
                        versionCode = versionInfo.versionCode,
                        versionName = versionInfo.versionName,
                        downloadUrl = versionInfo.downloadUrl,
                        mandatory = versionInfo.mandatory
                    )
                }
            }
            return@withContext UpdateResult.NoUpdate
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    fun downloadAndInstall(context: Context, url: String, fileName: String = "app-update.apk") {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading LeadHunters Update")
            setDescription("Downloading latest version...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
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
