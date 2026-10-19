package com.besenok.smarthome.domain.model

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class WeatherInfo(
    val city: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    val condition: String,
    val description: String,
    val iconCode: String,
    val windSpeed: Double = 0.0,
    val windDeg: Int = 0,
    val forecast: List<ForecastEntry>
) {
    data class ForecastEntry(
        // Timestamp
        val timestamp: Long,
        
        // Temperature (Main)
        val temperature: Double,
        val feelsLike: Double,
        val tempMin: Double,
        val tempMax: Double,
        val humidity: Int,
        val pressure: Int,
        
        // Weather condition
        val condition: String,
        val description: String,
        val iconCode: String,
        
        // Wind
        val windSpeed: Double,
        val windDeg: Int,
        val windGust: Double,
        
        // Clouds
        val cloudiness: Int,
        
        // Precipitation
        val precipitationProbability: Double,
        
        // Visibility
        val visibility: Int,
        
        // Day name
        val dayName: String = getDayName(timestamp)
    ) {
        companion object {
            private const val TAG = "WeatherInfo"
            
            private fun getDayName(timestamp: Long): String {
                if (timestamp == 0L) return ""
                // OpenWeatherMap возвращает timestamp в секундах, а Date() требует миллисекунды
                val date = Date(timestamp * 1000)
                val dateFormat = SimpleDateFormat("EEEE", Locale("ru"))
                val dayName = dateFormat.format(date)
                Log.d(TAG, "timestamp=$timestamp -> date=$date -> dayName=$dayName")
                return dayName
            }
        }
    }
}
