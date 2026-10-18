package com.besenok.smarthome.domain.usecase.weather

import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.domain.repository.WeatherRepository
import com.besenok.smarthome.ui.common.UiState
import javax.inject.Inject

/**
 * UseCase для получения прогноза погоды
 */
class GetForecastUseCase @Inject constructor(
    private val repository: WeatherRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): UiState<List<WeatherInfo.ForecastEntry>> {
        return repository.getForecast(lat, lon)
    }
}
