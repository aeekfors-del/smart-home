package com.besenok.smarthome.data.remote.weather.model

import kotlinx.serialization.Serializable

/**
 * Модель ответа Forecast 2.5 API
 * Документация: https://openweathermap.org/forecast
 */
@Serializable
data class OpenWeatherForecastResponse(
    val city: City? = null,
    val cod: String? = null,
    val message: Int? = null,
    val cnt: Int? = null,
    val list: List<ForecastItem>? = null
) {
    @Serializable
    data class City(
        val id: Int? = null,
        val name: String? = null,
        val coord: Coord? = null,
        val country: String? = null,
        val timezone: Int? = null
    )

    @Serializable
    data class Coord(
        val lon: Double? = null,
        val lat: Double? = null
    )

    @Serializable
    data class ForecastItem(
        val dt: Int? = null,
        val dt_txt: String? = null,
        val main: Main? = null,
        val weather: List<WeatherItem>? = null,
        val wind: Wind? = null,
        val visibility: Int? = null,
        val pop: Double? = null, // probability of precipitation
        val cloud: Cloud? = null,
        val sys: Sys? = null,
        val dt_txt_epoch: Long? = null
    ) {
        @Serializable
        data class Main(
            val temp: Double? = null,
            val feels_like: Double? = null,
            val temp_min: Double? = null,
            val temp_max: Double? = null,
            val pressure: Int? = null,
            val sea_level: Int? = null,
            val grnd_level: Int? = null,
            val humidity: Int? = null,
            val temp_kf: Double? = null
        )

        @Serializable
        data class WeatherItem(
            val id: Int? = null,
            val main: String? = null,
            val description: String? = null,
            val icon: String? = null
        )

        @Serializable
        data class Wind(
            val speed: Double? = null,
            val deg: Int? = null,
            val gust: Double? = null
        )

        @Serializable
        data class Cloud(
            val all: Int? = null
        )

        @Serializable
        data class Sys(
            val pod: String? = null
        )
    }
}
