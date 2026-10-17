package com.besenok.smarthome.domain.repository

import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.ui.common.UiState

interface WeatherRepository {
    suspend fun getCurrentWeather(lat: Double, lon: Double): UiState<WeatherInfo>
    suspend fun getForecast(lat: Double, lon: Double): UiState<List<WeatherInfo.ForecastEntry>>
}
