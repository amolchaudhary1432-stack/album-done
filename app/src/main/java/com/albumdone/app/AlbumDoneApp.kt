package com.albumdone.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AlbumDoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANALYSIS,
                "Photo analysis",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows progress while your photos are checked" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                "Analysis finished",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tells you when your photos are ready for the shortlist" }
        )
    }

    companion object {
        const val CHANNEL_ANALYSIS = "analysis"
        const val CHANNEL_DONE = "analysis_done"
    }
}
