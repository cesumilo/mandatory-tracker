package com.valoranttracker.app.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification as ANotification
import androidx.work.*
import com.valoranttracker.app.MainActivity
import com.valoranttracker.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class MatchNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            showNextMatchNotification()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun showNextMatchNotification() {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Match Notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications for Mandatory matches"
                }
            )
        }
        
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
        val nextMatch = data.data
            .filter { it.status.equals("Upcoming", ignoreCase = true) }
            .find { match -> 
                match.teams.any { team -> team.id == teamId }
            }
        
        if (nextMatch != null) {
            val opponent = nextMatch.teams.find { it.id != teamId }?.name ?: "TBD"
            val event = nextMatch.event
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val builder = ANotification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.mandatory_logo)
                .setContentTitle("Next Match")
                .setContentText("Mandatory vs $opponent - $event")
                .setPriority(ANotification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            
            notifManager.notify(nextMatch.id.hashCode(), builder.build())
        }
    }

    companion object {
        const val CHANNEL_ID = "match_notifications"
        const val WORK_NAME = "match_notification_worker"

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<MatchNotificationWorker>().build()
            )
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MatchNotificationWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}