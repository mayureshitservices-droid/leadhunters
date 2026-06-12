package com.example.leadhunters.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L && context != null) {
                installApk(context, downloadId)
            }
        }
    }

    private fun installApk(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        try {
            if (cursor.moveToFirst()) {
                val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusColumn >= 0 && cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL) {
                    val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (apkUri != null) {
                        android.util.Log.i("UpdateReceiver", "Installing APK from URI: $apkUri")
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            context.startActivity(installIntent)
                        } catch (e: Exception) {
                            android.util.Log.e("UpdateReceiver", "Failed to start installation: ${e.message}")
                        }
                    } else {
                        android.util.Log.e("UpdateReceiver", "Could not get URI for downloaded file")
                    }
                } else {
                    val status = if (statusColumn >= 0) cursor.getInt(statusColumn) else -1
                    android.util.Log.e("UpdateReceiver", "Download failed or incomplete. Status: $status")
                }
            }
        } finally {
            cursor.close()
        }
    }
}
