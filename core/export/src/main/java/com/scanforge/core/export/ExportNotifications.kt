package com.scanforge.core.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo

/** Builds the foreground-service progress notification shown while an export runs. */
internal object ExportNotifications {
    const val CHANNEL_ID = "scanforge_export"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Document export", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress while exporting documents to PDF or text"
                },
            )
        }
    }

    fun foregroundInfo(context: Context, title: String, completed: Int, total: Int): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (total > 0) "Page $completed of $total" else "Preparing…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), completed, total == 0)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
