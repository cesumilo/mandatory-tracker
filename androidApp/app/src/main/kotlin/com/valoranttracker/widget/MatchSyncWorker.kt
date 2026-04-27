package com.valoranttracker.app.widget

import android.content.Context
import androidx.work.*
import com.valoranttracker.app.VlrScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            // Use VlrScraper to get fresh data
            val upcomingMatches = VlrScraper.getUpcomingMatches(forceRefresh = true)

            if (upcomingMatches.isNotEmpty()) {
                val firstMatch = upcomingMatches.first()
                val opponent = firstMatch.teams.getOrNull(1)?.name ?: "TBD"
                val timeUntil = firstMatch.timeUntil.ifEmpty { "Soon" }

                MatchWidget.updateWidget(
                    context = context,
                    opponent = opponent,
                    event = firstMatch.event,
                    timeUntil = timeUntil,
                    tournament = firstMatch.tournament,
                )
            } else {
                // No upcoming matches - show "No upcoming match" state
                MatchWidget.updateWidget(
                    context = context,
                    opponent = null,
                    event = null,
                    timeUntil = null,
                    tournament = null,
                )
            }
        } catch (e: Exception) {
            // Keep existing data on error
            android.util.Log.e("MatchSyncWorker", "Error updating widget: ${e.message}")
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
