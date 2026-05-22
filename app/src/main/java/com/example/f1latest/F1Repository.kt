package com.example.f1latest

import android.content.Context

class F1Repository(context: Context? = null) {
    private val openF1 = OpenF1ApiService.create {
        context?.getSharedPreferences("f1_prefs", Context.MODE_PRIVATE)?.getString("openf1_api_key", null)
    }
    private val ergastPrimary = F1ApiService.create(F1ApiService.getPrimaryUrl())
    private val ergastFallback = F1ApiService.create(F1ApiService.getFallbackUrl())

    private suspend fun <T> withFallback(
        primaryCall: suspend () -> T,
        fallbackCall: suspend () -> T
    ): T? {
        return try {
            primaryCall()
        } catch (e: Exception) {
            try {
                fallbackCall()
            } catch (e2: Exception) {
                null
            }
        }
    }

    suspend fun getLatestErgastResults(): Race? {
        return withFallback(
            { ergastPrimary.getLatestResults().mrData.raceTable?.races?.firstOrNull() },
            { ergastFallback.getLatestResults().mrData.raceTable?.races?.firstOrNull() }
        )
    }

    suspend fun getLatestSession(): Session? {
        return try {
            openF1.getSessions("latest").firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLaps(sessionKey: Int): List<Lap> {
        return try {
            openF1.getLaps(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDrivers(sessionKey: Int): List<OpenF1Driver> {
        return try {
            openF1.getDrivers(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPositions(sessionKey: Int): List<Position> {
        return try {
            openF1.getPositions(sessionKey.toString())
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getRaceControl(sessionKey: Int): List<RaceControlMessage> {
        return try {
            openF1.getRaceControl(sessionKey.toString())
        } catch (e: Exception) {
            emptyList()
        }
    }
}
