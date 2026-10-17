package com.besenok.smarthome.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.domain.repository.WeatherRepository
import com.besenok.smarthome.domain.repository.TimeRepository
import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.domain.model.TimeInfo
import com.besenok.smarthome.ui.common.ConnectionState
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val iobrokerRepository: IobrokerRepository,
    private val weatherRepository: WeatherRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    val connectionState: StateFlow<com.besenok.smarthome.domain.repository.ConnectionState> =
        iobrokerRepository.connectionState

    private val _devicesState = MutableStateFlow<UiState<List<com.besenok.smarthome.domain.model.Device>>>(UiState.Loading)
    val devicesState: StateFlow<UiState<List<com.besenok.smarthome.domain.model.Device>>> = _devicesState.asStateFlow()

    private val _sensorsState = MutableStateFlow<UiState<List<com.besenok.smarthome.domain.model.Sensor>>>(UiState.Loading)
    val sensorsState: StateFlow<UiState<List<com.besenok.smarthome.domain.model.Sensor>>> = _sensorsState.asStateFlow()

    private val _weatherState = MutableStateFlow<UiState<WeatherInfo>>(UiState.Loading)
    val weatherState: StateFlow<UiState<WeatherInfo>> = _weatherState.asStateFlow()

    private val _timeState = MutableStateFlow<UiState<TimeInfo>>(UiState.Loading)
    val timeState: StateFlow<UiState<TimeInfo>> = _timeState.asStateFlow()

    init {
        viewModelScope.launch {
            iobrokerRepository.connect()
            loadDevices()
            loadSensors()
            loadWeather()
            loadTime()
        }
    }

    private fun loadDevices() {
        viewModelScope.launch {
            try {
                _devicesState.value = iobrokerRepository.getDevices()
            } catch (e: Exception) {
                _devicesState.value = UiState.Error(e.message ?: "Failed to load devices")
            }
        }
    }

    private fun loadSensors() {
        viewModelScope.launch {
            try {
                _sensorsState.value = iobrokerRepository.getSensors()
            } catch (e: Exception) {
                _sensorsState.value = UiState.Error(e.message ?: "Failed to load sensors")
            }
        }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            try {
                _weatherState.value = weatherRepository.getCurrentWeather(55.7558, 37.6173)
            } catch (e: Exception) {
                _weatherState.value = UiState.Error(e.message ?: "Failed to load weather")
            }
        }
    }

    private fun loadTime() {
        viewModelScope.launch {
            try {
                _timeState.value = timeRepository.getCurrentTime()
            } catch (e: Exception) {
                _timeState.value = UiState.Error(e.message ?: "Failed to load time")
            }
        }
    }
}
