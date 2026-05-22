package com.example.f1latest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

class F1SessionWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val repository = F1Repository(appContext)
    private val prefs: SharedPreferences = appContext.getSharedPreferences("f1_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val session = repository.getLatestSession() ?: return ListenableWorker.Result.success()
            val sessionType = session.sessionType.lowercase()

            val positions = repository.getPositions(session.sessionKey)
            val raceControl = repository.getRaceControl(session.sessionKey)

            if (positions.isEmpty()) return ListenableWorker.Result.success()

            // Find positions for our targeted drivers
            val targetedDrivers = listOf(1, 16, 12)
            val currentPositions = positions
                .groupBy { it.driverNumber }
                .mapValues { it.value.maxByOrNull { pos -> pos.date }?.position ?: 99 }

            val top1 = currentPositions.entries.minByOrNull { it.value }?.key
            val top3 = currentPositions.entries.sortedBy { it.value }.take(3).map { it.key }

            var notificationMessage = ""

            if (sessionType.contains("practice")) {
                val prevTop1 = prefs.getInt("prev_top_1_${session.sessionKey}", -1)
                if (prevTop1 != -1 && top1 != null && top1 != prevTop1) {
                    notificationMessage += "New P1: Driver $top1! "
                }
                top1?.let { prefs.edit().putInt("prev_top_1_${session.sessionKey}", it).apply() }

                for (driver in targetedDrivers) {
                    val currentPos = currentPositions[driver]
                    val prevPos = prefs.getInt("prev_pos_${driver}_${session.sessionKey}", 99)
                    if (currentPos != null && currentPos < prevPos && prevPos != 99) {
                        notificationMessage += "Driver $driver improved to P$currentPos! "
                    }
                    if (currentPos != null) {
                        prefs.edit().putInt("prev_pos_${driver}_${session.sessionKey}", currentPos).apply()
                    }
                }
            } else if (sessionType.contains("qualifying")) {
                val prevTop3Str = prefs.getString("prev_top_3_${session.sessionKey}", "")
                val currentTop3Str = top3.joinToString(",")
                if (prevTop3Str?.isNotEmpty() == true && prevTop3Str != currentTop3Str) {
                    notificationMessage += "Top 3 changed! "
                }
                prefs.edit().putString("prev_top_3_${session.sessionKey}", currentTop3Str).apply()

                for (driver in targetedDrivers) {
                    val currentPos = currentPositions[driver]
                    val prevPos = prefs.getInt("prev_pos_${driver}_${session.sessionKey}", 99)
                    if (currentPos != null && currentPos < prevPos && prevPos != 99) {
                        notificationMessage += "Driver $driver improved to P$currentPos! "
                    }
                    if (currentPos != null) {
                        prefs.edit().putInt("prev_pos_${driver}_${session.sessionKey}", currentPos).apply()
                    }
                }
            } else if (sessionType.contains("race")) {
                val latestFlag = raceControl.lastOrNull { it.category == "Flag" }
                val prevFlag = prefs.getString("prev_flag_${session.sessionKey}", "")
                if (latestFlag != null && latestFlag.flag != prevFlag && latestFlag.flag != "CLEAR") {
                    notificationMessage += "Flag: ${latestFlag.flag}! "
                    prefs.edit().putString("prev_flag_${session.sessionKey}", latestFlag.flag).apply()
                }

                for (driver in targetedDrivers) {
                    val currentPos = currentPositions[driver]
                    val prevPos = prefs.getInt("prev_pos_${driver}_${session.sessionKey}", 99)
                    if (currentPos != null && currentPos < prevPos && prevPos != 99) {
                        notificationMessage += "Driver $driver overtook to P$currentPos! "
                    }
                    if (currentPos != null) {
                        prefs.edit().putInt("prev_pos_${driver}_${session.sessionKey}", currentPos).apply()
                    }
                }
            }

            if (notificationMessage.isNotEmpty()) {
                sendNotification("F1 Update: ${session.sessionName}", notificationMessage)
            }

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            ListenableWorker.Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "f1_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "F1 Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
