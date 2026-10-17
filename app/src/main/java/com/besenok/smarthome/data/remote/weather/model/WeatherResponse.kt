package com.besenok.smarthome.data.remote.weather.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherMapResponse(
    val coord: Coord? = null,
    val weather: List<WeatherItem>? = null,
    val main: Main? = null,
    val name: String? = null,
    val wind: Wind? = null
) {
    @Serializable
    data class Coord(
        val lon: Double? = null,
        val lat: Double? = null
    )

    @Serializable
    data class WeatherItem(
        val main: String? = null,
        val description: String? = null,
        val icon: String? = null
    )

    @Serializable
    data class Main(
        val temp: Double? = null,
        val feels_like: Double? = null,
        val temp_min: Double? = null,
        val temp_max: Double? = null,
        val pressure: Int? = null,
        val humidity: Int? = null
    )

    @Serializable
    data class Wind(
        val speed: Double? = null,
        val deg: Int? = null
    )
}
