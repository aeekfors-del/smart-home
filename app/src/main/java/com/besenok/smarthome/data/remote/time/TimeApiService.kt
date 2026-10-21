package com.besenok.smarthome.data.remote.time

import com.besenok.smarthome.data.remote.time.model.TimeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeApiService @Inject constructor(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://worldtimeapi.org/api"
    }

    suspend fun getCurrentTime(): TimeResponse {
        return httpClient.get("$BASE_URL/ip").body()
    }
}
