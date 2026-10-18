package com.besenok.smarthome.data.remote.weather

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.data.remote.weather.model.OpenWeatherForecastResponse
import com.besenok.smarthome.data.remote.weather.model.OpenWeatherMapResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeatherApiService"

@Singleton
class WeatherApiService @Inject constructor(
    private val preferences: AppPreferencesDataSource
) {
    private companion object {
        const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getCurrentWeather(lat: Double, lon: Double): OpenWeatherMapResponse {
        val apiKey = getApiKey()
        
        return client.get(BASE_URL) {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("appid", apiKey)
            parameter("units", "metric")
            parameter("lang", "ru")
        }.body()
    }

    /**
     * Получение прогноза на 5 дней (Forecast 2.5 API)
     * Документация: https://openweathermap.org/forecast
     * @return Список прогнозов (каждые 3 часа, всего 40 записей)
     */
    suspend fun getForecast(lat: Double, lon: Double): OpenWeatherForecastResponse {
        val apiKey = getApiKey()
        val forecastUrl = "https://api.openweathermap.org/data/2.5/forecast"
        
        Log.d(TAG, "Getting forecast for lat=$lat, lon=$lon")
        
        return client.get(forecastUrl) {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("appid", apiKey)
            parameter("units", "metric")
            parameter("lang", "ru")
        }.body()
    }

    private suspend fun getApiKey(): String {
        return preferences.openweathermapApiKey.first()
            .takeIf { it.isNotBlank() }
            ?: AppPreferencesDataSource.DEFAULT_OPENWEATHERMAP_API_KEY
    }
}
