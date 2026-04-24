package com.valoranttracker.app.widget

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
            scheduleNextNotification()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun scheduleNextNotification() {
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
            val matchTimeMs = nextMatch.timestamp * 1000
            val oneHourBefore = matchTimeMs - (60 * 60 * 1000)
            val now = System.currentTimeMillis()
            
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val scheduledMatchId = prefs.getString("scheduled_notification_match_id", null)
            
            if (nextMatch.id != scheduledMatchId) {
                cancelExistingAlarm(nextMatch.id)
                
                when {
                    oneHourBefore > now -> {
                        scheduleExactNotification(nextMatch.id, nextMatch.event, nextMatch.teams.find { it.id != teamId }?.name ?: "TBD", oneHourBefore)
                        prefs.edit().putString("scheduled_notification_match_id", nextMatch.id).apply()
                    }
                    matchTimeMs > now -> {
                        showImmediateNotification(nextMatch.id, nextMatch.event, nextMatch.teams.find { it.id != teamId }?.name ?: "TBD")
                        prefs.edit().putString("scheduled_notification_match_id", nextMatch.id).apply()
                    }
                    else -> {
                        prefs.edit().remove("scheduled_notification_match_id").apply()
                    }
                }
            }
        } else {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit().remove("scheduled_notification_match_id").apply()
        }
    }

    private fun cancelExistingAlarm(matchId: String) {
        try {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                matchId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(it)
                it.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showImmediateNotification(matchId: String, event: String, opponent: String) {
        createNotificationChannel()
        
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = ANotification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.mandatory_logo)
            .setContentTitle("Match starting soon!")
            .setContentText("Mandatory vs $opponent - $event")
            .setPriority(ANotification.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(matchId.hashCode(), notification)
    }

    private fun scheduleExactNotification(matchId: String, event: String, opponent: String, triggerTimeMs: Long) {
        createNotificationChannel()
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("match_id", matchId)
            putExtra("opponent", opponent)
            putExtra("event", event)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Match Notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications for Mandatory matches"
                }
            )
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

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra("match_id") ?: return
        val opponent = intent.getStringExtra("opponent") ?: "TBD"
        val event = intent.getStringExtra("event") ?: "Match"
        
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(MatchNotificationWorker.CHANNEL_ID, "Match Notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications for Mandatory matches"
                }
            )
        }
        
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = ANotification.Builder(context, MatchNotificationWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.mandatory_logo)
            .setContentTitle("Match in 1 hour!")
            .setContentText("Mandatory vs $opponent - $event")
            .setPriority(ANotification.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notifManager.notify(matchId.hashCode(), notification)
    }
}