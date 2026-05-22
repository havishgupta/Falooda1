package com.example.f1latest

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ErgastApiTest {
    @Test
    fun testGetLatestResults() = runBlocking {
        val api = F1ApiService.create()
        try {
            val response = api.getLatestResults()
            println("Response: $response")
            assertNotNull(response.mrData.raceTable?.races)
            assertTrue(response.mrData.raceTable!!.races.isNotEmpty())
        } catch (e: Exception) {
            e.printStackTrace()
            fail("API call failed: ${e.message}")
        }
    }
}
