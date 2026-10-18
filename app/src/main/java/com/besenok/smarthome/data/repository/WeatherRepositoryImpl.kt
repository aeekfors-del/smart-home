package com.besenok.smarthome.data.repository

import android.util.Log
import com.besenok.smarthome.data.remote.weather.WeatherApiService
import com.besenok.smarthome.data.remote.weather.model.OpenWeatherForecastResponse
import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.domain.repository.WeatherRepository
import com.besenok.smarthome.ui.common.UiState
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeatherRepositoryImpl"

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
            Log.d(TAG, "Getting forecast for lat=$lat, lon=$lon")
            val response = weatherApiService.getForecast(lat, lon)
            
            val forecastList = response.list ?: emptyList()
            Log.d(TAG, "Got ${forecastList.size} forecast entries")
            
            // Группируем по дням и берем по одной записи на день (полдень)
            val dailyForecast = groupForecastByDay(forecastList)
            
            Log.d(TAG, "Grouped into ${dailyForecast.size} daily forecasts")
            UiState.Success(dailyForecast)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting forecast", e)
            UiState.Error("Ошибка получения прогноза: ${e.message}")
        }
    }
    
    /**
     * Группирует прогноз по дням и выбирает запись для полудня (12:00)
     */
    private fun groupForecastByDay(forecastList: List<OpenWeatherForecastResponse.ForecastItem>): List<WeatherInfo.ForecastEntry> {
        // Формат даты для группировки: yyyy-MM-dd
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayMap = mutableMapOf<String, OpenWeatherForecastResponse.ForecastItem>()
        
        for (item in forecastList) {
            val dateStr = item.dt_txt ?: continue
            val datePart = dateStr.substring(0, 10) // "2024-01-15"
            
            // Предпочитаем запись ближе к полудню (12:00 - 15:00)
            val timePart = dateStr.substring(11, 16) // "HH:mm"
            val hour = timePart.substring(0, 2).toIntOrNull() ?: 0
            
            val existing = dayMap[datePart]
            if (existing == null) {
                // Первая запись для этого дня
                dayMap[datePart] = item
            } else {
                // Выбираем запись ближе к полудню
                val existingHour = existing.dt_txt?.substring(11, 13)?.toIntOrNull() ?: 0
                // Если текущая запись ближе к 12:00, заменяем
                if (Math.abs(hour - 12) < Math.abs(existingHour - 12)) {
                    dayMap[datePart] = item
                }
            }
        }
        
        // Преобразуем в ForecastEntry
        return dayMap.values.mapNotNull { item ->
            val main = item.main ?: return@mapNotNull null
            val weatherItem = item.weather?.firstOrNull() ?: return@mapNotNull null
            
            WeatherInfo.ForecastEntry(
                timestamp = item.dt?.toLong() ?: 0,
                temperature = main.temp ?: 0.0,
                condition = weatherItem.main ?: "Неизвестно",
                iconCode = weatherItem.icon ?: "01d"
            )
        }
    }
}
