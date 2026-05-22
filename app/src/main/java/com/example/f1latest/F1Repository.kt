package com.example.f1latest

class F1Repository {
    private val openF1 = OpenF1ApiService.create()

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
