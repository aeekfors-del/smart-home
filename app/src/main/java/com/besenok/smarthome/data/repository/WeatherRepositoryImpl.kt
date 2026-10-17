package com.besenok.smarthome.data.repository

import com.besenok.smarthome.data.remote.weather.WeatherApiService
import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.domain.repository.WeatherRepository
import com.besenok.smarthome.ui.common.UiState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val weatherApiService: WeatherApiService
) : WeatherRepository {

    override suspend fun getCurrentWeather(lat: Double, lon: Double): UiState<WeatherInfo> {
        return try {
            val response = weatherApiService.getCurrentWeather(lat, lon)
            
            val main = response.main ?: return UiState.Error("Нет данных о погоде")
            val weatherItem = response.weather?.firstOrNull() ?: return UiState.Error("Нет данных о состоянии погоды")
            
            // Конвертация давления из hPa в мм рт. ст.
            val pressureMm = (main.pressure ?: 0) * 3 / 4
            
            val wind = response.wind
            
            val weatherInfo = WeatherInfo(
                city = response.name ?: "Неизвестно",
                temperature = main.temp ?: 0.0,
                feelsLike = main.feels_like ?: main.temp ?: 0.0,
                humidity = main.humidity ?: 0,
                pressure = pressureMm,
                condition = weatherItem.main ?: "Неизвестно",
                description = weatherItem.description ?: "Нет данных",
                iconCode = weatherItem.icon ?: "01d",
                windSpeed = wind?.speed ?: 0.0,
                windDeg = wind?.deg ?: 0,
                forecast = emptyList()
            )
            UiState.Success(weatherInfo)
        } catch (e: Exception) {
            UiState.Error("Ошибка получения погоды: ${e.message}")
        }
    }

    override suspend fun getForecast(lat: Double, lon: Double): UiState<List<WeatherInfo.ForecastEntry>> {
        return try {
            // OpenWeatherMap free tier doesn't support forecast in the same endpoint
            // Returning empty forecast for now
            UiState.Success(emptyList())
        } catch (e: Exception) {
            UiState.Error("Ошибка получения прогноза: ${e.message}")
        }
    }
}
