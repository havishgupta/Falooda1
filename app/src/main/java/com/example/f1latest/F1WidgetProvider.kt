package com.example.f1latest

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout


class F1WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            setInitialViews(context, appWidgetManager, appWidgetId)
        }
        val refreshIntent = Intent(context, F1WidgetProvider::class.java).apply { action = ACTION_REFRESH }
        context.sendBroadcast(refreshIntent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Always call this on the main thread
        
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(8500) {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val componentName = ComponentName(context, F1WidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                        
                        val resultData = fetchF1Data()
                        
                        for (appWidgetId in appWidgetIds) {
                            val views = RemoteViews(context.packageName, R.layout.f1_widget)
                            
                            val refreshIntent = Intent(context, F1WidgetProvider::class.java).apply { action = ACTION_REFRESH }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.btn_refresh, pendingIntent)

                            val tvIds = listOf(
                                R.id.tv_driver_1, R.id.tv_driver_2, R.id.tv_driver_3,
                                R.id.tv_driver_4, R.id.tv_driver_5, R.id.tv_driver_6
                            )
                            for (id in tvIds) views.setTextViewText(id, "")

                            if (resultData.error != null) {
                                views.setTextViewText(R.id.tv_widget_title, "Error: ${resultData.error}")
                            } else {
                                views.setTextViewText(R.id.tv_widget_title, resultData.raceName)
                                for (i in resultData.drivers.indices) {
                                    if (i < tvIds.size) {
                                        views.setTextViewText(tvIds[i], resultData.drivers[i])
                                    }
                                }
                            }
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val componentName = ComponentName(context, F1WidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                        for (appWidgetId in appWidgetIds) {
                            val views = RemoteViews(context.packageName, R.layout.f1_widget)
                            
                            val refreshIntent = Intent(context, F1WidgetProvider::class.java).apply { action = ACTION_REFRESH }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.btn_refresh, pendingIntent)
                            
                            views.setTextViewText(R.id.tv_widget_title, "Error: Connection Timed Out")
                            val tvIds = listOf(
                                R.id.tv_driver_1, R.id.tv_driver_2, R.id.tv_driver_3,
                                R.id.tv_driver_4, R.id.tv_driver_5, R.id.tv_driver_6
                            )
                            for (id in tvIds) views.setTextViewText(id, "")
                            
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun setInitialViews(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.f1_widget)
        views.setTextViewText(R.id.tv_widget_title, "F1 Latest...")
        
        val refreshIntent = Intent(context, F1WidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun fetchF1Data(): WidgetData {
        val repository = F1Repository()
        return try {
            coroutineScope {
                // Fetch the latest session status, race results, and qualifying in parallel
                val latestSessionDeferred = async {
                    try {
                        withTimeout(4000) {
                            repository.getLatestSession()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val latestRaceDeferred = async {
                    try {
                        withTimeout(4500) {
                            repository.getLatestResults()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val latestQualDeferred = async {
                    try {
                        withTimeout(4500) {
                            repository.getLatestQualifying()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                val latestSession = latestSessionDeferred.await()

                if (latestSession != null && latestSession.sessionType.contains("Practice", ignoreCase = true)) {
                    // Fetch practice details (laps & drivers) in parallel
                    val lapsDeferred = async {
                        try {
                            withTimeout(3500) { repository.getLaps(latestSession.sessionKey) }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    val driversDeferred = async {
                        try {
                            withTimeout(3500) { repository.getDrivers(latestSession.sessionKey) }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }

                    val laps = lapsDeferred.await()
                    val driversInfo = driversDeferred.await().associateBy { it.driverNumber }

                    val fastestLaps = laps
                        .filter { it.lapDuration != null && it.lapDuration > 0 && !it.isPitOutLap }
                        .groupBy { it.driverNumber }
                        .mapValues { it.value.minOf { lap -> lap.lapDuration!! } }
                        .toList()
                        .sortedBy { it.second }
                        .take(5)

                    if (fastestLaps.isNotEmpty()) {
                        val drivers = fastestLaps.mapIndexed { i, pair ->
                            val info = driversInfo[pair.first]
                            val time = String.format("%.3f", pair.second)
                            "${i + 1}. ${info?.fullName ?: "Driver ${pair.first}"} - $time"
                        }
                        return@coroutineScope WidgetData(raceName = "${latestSession.circuitName} - ${latestSession.sessionName}", drivers = drivers)
                    }
                }

                val latestRace = latestRaceDeferred.await()
                val latestQual = latestQualDeferred.await()

                if (latestRace == null && latestQual == null) {
                    WidgetData(error = "Network/DNS Error. Check connection.")
                } else {
                    val showQual = if (latestRace != null && latestQual != null) {
                        val raceRound = latestRace.round.toIntOrNull() ?: 0
                        val qualRound = latestQual.round.toIntOrNull() ?: 0
                        qualRound > raceRound
                    } else {
                        latestRace == null
                    }

                    if (showQual && latestQual != null) {
                        val drivers = latestQual.qualifyingResults?.take(5)?.map {
                            "${it.position}. ${it.driver.givenName} ${it.driver.familyName}"
                        } ?: emptyList()
                        WidgetData(raceName = "${latestQual.raceName} (Quali)", drivers = drivers)
                    } else if (latestRace != null) {
                        val drivers = latestRace.results?.take(5)?.map {
                            "${it.position}. ${it.driver.givenName} ${it.driver.familyName} - ${it.points} pts"
                        } ?: emptyList()
                        WidgetData(raceName = latestRace.raceName, drivers = drivers)
                    } else {
                        WidgetData(error = "No session data")
                    }
                }
            }
        } catch (e: Exception) {
            WidgetData(error = e.localizedMessage ?: "Unknown Error")
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.f1latest.WIDGET_REFRESH"
    }
}

data class WidgetData(
    val raceName: String = "",
    val drivers: List<String> = emptyList(),
    val error: String? = null
)
