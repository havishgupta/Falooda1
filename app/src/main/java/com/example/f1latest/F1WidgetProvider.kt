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
        super.onReceive(context, intent) 
        
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(8500) {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val componentName = ComponentName(context, F1WidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                        
                        val resultData = fetchF1Data(context)
                        
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
                                views.setTextViewText(R.id.tv_widget_title, resultData.sessionName)
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

    private suspend fun fetchF1Data(context: Context): WidgetData {
        val repository = F1Repository(context)
        return try {
            coroutineScope {
                val latestSession = try {
                    withTimeout(4000) {
                        repository.getLatestSession()
                    }
                } catch (e: Exception) {
                    null
                }

                if (latestSession != null) {
                    val positionsDeferred = async {
                        try {
                            withTimeout(3500) { repository.getPositions(latestSession.sessionKey) }
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

                    val positions = positionsDeferred.await()
                    val driversInfo = driversDeferred.await().associateBy { it.driverNumber }

                    val latestPositions = positions
                        .groupBy { it.driverNumber }
                        .mapValues { it.value.maxByOrNull { pos -> pos.date } }
                        .values
                        .filterNotNull()
                        .sortedBy { it.position }
                        .take(5)

                    if (latestPositions.isNotEmpty()) {
                        val drivers = latestPositions.map { pos ->
                            val info = driversInfo[pos.driverNumber]
                            "P${pos.position} - ${info?.fullName ?: "Driver ${pos.driverNumber}"}"
                        }
                        return@coroutineScope WidgetData(sessionName = "${latestSession.circuitName} - ${latestSession.sessionName}", drivers = drivers)
                    } else {
                        // Attempt Fallback if no positions
                        return@coroutineScope fetchFallbackWidgetData(repository)
                    }
                } else {
                    return@coroutineScope fetchFallbackWidgetData(repository)
                }
            }
        } catch (e: Exception) {
            WidgetData(error = e.localizedMessage ?: "Unknown Error")
        }
    }

    private suspend fun fetchFallbackWidgetData(repository: F1Repository): WidgetData {
        return try {
            val fallbackRace = repository.getLatestErgastResults()
            if (fallbackRace != null) {
                val drivers = fallbackRace.results?.take(5)?.map {
                    "${it.position}. ${it.driver.givenName} ${it.driver.familyName} - ${it.points} pts"
                } ?: emptyList()
                WidgetData(sessionName = fallbackRace.raceName, drivers = drivers)
            } else {
                WidgetData(error = "No active session")
            }
        } catch (e: Exception) {
            WidgetData(error = "Fallback failed: ${e.message}")
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.f1latest.WIDGET_REFRESH"
    }
}

data class WidgetData(
    val sessionName: String = "",
    val drivers: List<String> = emptyList(),
    val error: String? = null
)
