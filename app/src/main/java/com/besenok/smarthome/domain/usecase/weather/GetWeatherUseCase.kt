package com.besenok.smarthome.domain.usecase.weather

import com.besenok.smarthome.domain.repository.WeatherRepository
import javax.inject.Inject

class GetWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double) = repository.getCurrentWeather(lat, lon)
}
