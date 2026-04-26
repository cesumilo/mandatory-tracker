package com.valoranttracker.app.widget

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class MatchSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                fetchAndUpdateWidget()
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }

    private suspend fun fetchAndUpdateWidget() {
        try {
            val url = java.net.URL("https://vlr.orlandomm.net/api/v1/matches")
            val client = url.openConnection() as java.net.HttpURLConnection
            client.setRequestProperty("User-Agent", "ValorantWidget/1.0")
            client.connectTimeout = 10000
            client.readTimeout = 10000

            val response = client.inputStream.bufferedReader().readText()
            client.disconnect()

            val json = Json { ignoreUnknownKeys = true }
            val data = json.decodeFromString<ApiResponse>(response)

            val teamId = "7967"
            val upcomingMatch =
                data.data
                    .filter { it.status.equals("Upcoming", ignoreCase = true) }
                    .find { match ->
                        match.teams.any { team -> team.id == teamId }
                    }

            if (upcomingMatch != null) {
                val opponent =
                    upcomingMatch.teams
                        .find { it.id != teamId }?.name
                        ?: "TBD"

                val matchTimeMs = upcomingMatch.timestamp * 1000
                val now = System.currentTimeMillis()
                val diff = matchTimeMs - now
                val hours = diff / (1000 * 60 * 60)
                val mins = (diff % (1000 * 60 * 60)) / (1000 * 60)
                val timeUntil =
                    if (hours > 0) "${hours}h ${mins}m" else if (mins > 0) "${mins}m" else "now"

                MatchWidget.updateWidget(
                    context = context,
                    opponent = opponent,
                    event = upcomingMatch.event,
                    timeUntil = timeUntil,
                    tournament = upcomingMatch.tournament,
                )
            }
        } catch (e: Exception) {
            // Keep existing data on error
        }
    }

    companion object {
        const val WORK_NAME = "match_sync_worker"

        fun buildRequest(): PeriodicWorkRequest {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            return PeriodicWorkRequestBuilder<MatchSyncWorker>(
                30,
                TimeUnit.MINUTES,
                15,
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.MINUTES,
                )
                .build()
        }

        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildRequest(),
            )
        }

        fun requestImmediate(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<MatchSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

@Serializable
data class ApiResponse(
    val status: String = "",
    val size: Int = 0,
    val data: List<MatchData> = emptyList(),
)

@Serializable
data class MatchData(
    val id: String = "",
    val teams: List<TeamData> = emptyList(),
    val status: String = "",
    val event: String = "",
    val tournament: String = "",
    val img: String? = null,
    @SerialName("in")
    val timeUntil: String = "",
    val timestamp: Long = 0L,
)

@Serializable
data class TeamData(
    val id: String? = null,
    val name: String = "",
    val country: String? = null,
    val score: String? = null,
    val logo: String? = null,
)
