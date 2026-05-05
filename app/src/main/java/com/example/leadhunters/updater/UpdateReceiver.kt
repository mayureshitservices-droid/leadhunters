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
        if (cursor.moveToFirst()) {
            val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusColumn >= 0 && cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL) {
                val localUriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                if (localUriColumn >= 0) {
                    val localUriString = cursor.getString(localUriColumn)
                    val apkUri = Uri.parse(localUriString)
                    
                    val apkFile = File(apkUri.path ?: "")
                    if (apkFile.exists()) {
                         val contentUri = FileProvider.getUriForFile(
                             context,
                             "${context.packageName}.provider",
                             apkFile
                         )
                         val installIntent = Intent(Intent.ACTION_VIEW).apply {
                             setDataAndType(contentUri, "application/vnd.android.package-archive")
                             flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                         }
                         context.startActivity(installIntent)
                    }
                }
            }
        }
        cursor.close()
    }
}
