package com.valoranttracker.app.widget

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

suspend fun getNextMatchInfo(context: Context): Pair<String, String>? =
    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://vlr.orlandomm.net/api/v1/matches")
            val client = url.openConnection() as HttpURLConnection
            client.setRequestProperty("User-Agent", "ValorantWidget/1.0")
            client.connectTimeout = 10000
            client.readTimeout = 10000

            val response = client.inputStream.bufferedReader().readText()
            client.disconnect()

            val json = Json { ignoreUnknownKeys = true }
            val data = json.decodeFromString<ApiResponse>(response)

            val teamId = "7967"
            val nextMatch =
                data.data
                    .filter { it.status.equals("Upcoming", ignoreCase = true) }
                    .find { match ->
                        match.teams.any { team -> team.id == teamId }
                    }

            if (nextMatch != null) {
                val matchTimeMs = nextMatch.timestamp * 1000
                val oneHourBefore = matchTimeMs - (60 * 60 * 1000)
                val now = System.currentTimeMillis()

                val opponent = nextMatch.teams.find { it.id != teamId }?.name ?: "TBD"
                val notificationTime =
                    if (oneHourBefore > now) {
                        oneHourBefore
                    } else if (matchTimeMs > now) {
                        matchTimeMs
                    } else {
                        null
                    }

                Pair(
                    opponent,
                    notificationTime?.let {
                        val diff = it - now
                        val hours = diff / (1000 * 60 * 60)
                        val mins = (diff % (1000 * 60 * 60)) / (1000 * 60)
                        if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    } ?: "passed",
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
