package com.valoranttracker.app.widget

import android.content.Context
import com.valoranttracker.app.VlrScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getNextMatchInfo(context: Context): Pair<String, String>? =
    withContext(Dispatchers.IO) {
        try {
            val upcomingMatches = VlrScraper.getUpcomingMatches()

            if (upcomingMatches.isNotEmpty()) {
                val nextMatch = upcomingMatches.first()
                val opponent = nextMatch.teams.getOrNull(1)?.name ?: "TBD"
                val timeUntil = nextMatch.timeUntil.ifEmpty { "Soon" }

                return@withContext Pair(opponent, timeUntil)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MatchInfo", "Error getting next match info: ${e.message}")
            null
        }
    }
