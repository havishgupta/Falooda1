package com.example.f1latest

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class RaceControlMessage(
    @Json(name = "category") val category: String?,
    @Json(name = "flag") val flag: String?,
    @Json(name = "message") val message: String?,
    @Json(name = "scope") val scope: String?,
    @Json(name = "sector") val sector: Int?
)

data class IntervalData(
    @Json(name = "driver_number") val driverNumber: Int,
    @Json(name = "gap_to_leader") val gapToLeader: Double?,
    @Json(name = "interval") val interval: Double?
)

data class Session(
    @Json(name = "session_key") val sessionKey: Int,
    @Json(name = "session_name") val sessionName: String,
    @Json(name = "session_type") val sessionType: String,
    @Json(name = "date_start") val dateStart: String,
    @Json(name = "date_end") val dateEnd: String,
    @Json(name = "circuit_short_name") val circuitName: String
)

data class Lap(
    @Json(name = "driver_number") val driverNumber: Int,
    @Json(name = "lap_duration") val lapDuration: Double?,
    @Json(name = "is_pit_out_lap") val isPitOutLap: Boolean
)

data class Position(
    @Json(name = "session_key") val sessionKey: Int,
    @Json(name = "driver_number") val driverNumber: Int,
    @Json(name = "date") val date: String,
    @Json(name = "position") val position: Int
)

data class OpenF1Driver(
    @Json(name = "driver_number") val driverNumber: Int,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "name_acronym") val nameAcronym: String,
    @Json(name = "team_name") val teamName: String
)

interface OpenF1ApiService {
    @GET("sessions")
    suspend fun getSessions(
        @Query("session_key") sessionKey: String = "latest"
    ): List<Session>

    @GET("laps")
    suspend fun getLaps(
        @Query("session_key") sessionKey: Int
    ): List<Lap>

    @GET("drivers")
    suspend fun getDrivers(
        @Query("session_key") sessionKey: Int
    ): List<OpenF1Driver>

    @GET("race_control")
    suspend fun getRaceControl(
        @Query("session_key") sessionKey: String = "latest"
    ): List<RaceControlMessage>

    @GET("intervals")
    suspend fun getIntervals(
        @Query("session_key") sessionKey: String = "latest",
        @Query("driver_number") drivers: List<Int> = listOf(1, 16, 12)
    ): List<IntervalData>

    @GET("position")
    suspend fun getPositions(
        @Query("session_key") sessionKey: String = "latest"
    ): List<Position>

    companion object {
        private const val BASE_URL = "https://api.openf1.org/v1/"

        fun create(tokenProvider: (() -> String?)? = null): OpenF1ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                        .header("User-Agent", "F1LatestApp/1.0")
                    
                    val token = tokenProvider?.invoke()
                    if (!token.isNullOrBlank()) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                    
                    chain.proceed(requestBuilder.build())
                }
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenF1ApiService::class.java)
        }
    }
}
