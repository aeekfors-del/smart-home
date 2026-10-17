package com.besenok.smarthome.ui.screens.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.domain.usecase.weather.GetWeatherUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val getWeatherUseCase: GetWeatherUseCase
) : ViewModel() {

    private val _weather = MutableStateFlow<UiState<WeatherInfo>>(UiState.Loading)
    val weather: StateFlow<UiState<WeatherInfo>> = _weather.asStateFlow()

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    fun loadWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val state = getWeatherUseCase(lat, lon)
                _weather.value = state
                if (state is UiState.Success) {
                    _city.value = state.data.city
                }
            } catch (e: Exception) {
                _weather.value = UiState.Error(e.message ?: "Failed to load weather")
            }
        }
    }

    val currentTemp: Double
        get() = weather.value.let {
            when (it) {
                is UiState.Success -> it.data.temperature
                else -> 0.0
            }
        }

    val condition: String
        get() = weather.value.let {
            when (it) {
                is UiState.Success -> it.data.condition
                else -> ""
            }
        }
}
