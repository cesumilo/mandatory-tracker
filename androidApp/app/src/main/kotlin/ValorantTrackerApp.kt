package com.valoranttracker.app

import android.app.Application
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
    }
}