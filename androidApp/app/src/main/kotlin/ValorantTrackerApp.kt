package com.valoranttracker.app

import android.app.Application
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.valoranttracker.app.widget.MatchNotificationWorker
import com.valoranttracker.app.widget.MatchSyncWorker

class ValorantTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val hasCachedData = prefs.getString("opponent", null) != null
        
        if (hasCachedData) {
            MatchSyncWorker.enqueuePeriodic(this)
        } else {
            MatchSyncWorker.requestImmediate(this)
            MatchSyncWorker.enqueuePeriodic(this)
        }
        
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        if (hasNotificationPermission) {
            MatchNotificationWorker.schedule(this)
        }
    }
}