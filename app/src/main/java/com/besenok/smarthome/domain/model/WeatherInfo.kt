package com.besenok.smarthome.domain.model

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
        val timestamp: Long,
        val temperature: Double,
        val condition: String,
        val iconCode: String
    )
}
