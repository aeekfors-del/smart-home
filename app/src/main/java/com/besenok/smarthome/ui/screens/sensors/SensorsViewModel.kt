package com.besenok.smarthome.ui.screens.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.Sensor
import com.besenok.smarthome.domain.usecase.sensor.GetSensorsUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val getSensorsUseCase: GetSensorsUseCase
) : ViewModel() {

    private val _sensors = MutableStateFlow<UiState<List<Sensor>>>(UiState.Loading)
    val sensors: StateFlow<UiState<List<Sensor>>> = _sensors.asStateFlow()

    init {
        loadSensors()
    }

    fun loadSensors() {
        viewModelScope.launch {
            try {
                _sensors.value = getSensorsUseCase()
            } catch (e: Exception) {
                _sensors.value = UiState.Error(e.message ?: "Failed to load sensors")
            }
        }
    }

    val temperatureSensor: Sensor?
        get() = sensors.value.let {
            when (it) {
                is UiState.Success -> it.data.find { s -> s.sensorType == Sensor.SensorType.TEMPERATURE }
                else -> null
            }
        }

    val humiditySensor: Sensor?
        get() = sensors.value.let {
            when (it) {
                is UiState.Success -> it.data.find { s -> s.sensorType == Sensor.SensorType.HUMIDITY }
                else -> null
            }
        }
}
