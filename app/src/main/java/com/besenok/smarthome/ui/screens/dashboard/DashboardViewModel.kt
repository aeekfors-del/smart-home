package com.besenok.smarthome.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DashboardViewModel"
private const val PARAMETER_REFRESH_INTERVAL_MS = 10_000L

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val iobrokerRepository: IobrokerRepository,
    private val weatherRepository: WeatherRepository,
    private val timeRepository: TimeRepository,
    private val preferences: AppPreferencesDataSource
) : ViewModel() {

    val connectionState: StateFlow<com.besenok.smarthome.domain.repository.ConnectionState> =
        iobrokerRepository.connectionState

    private val _devicesState = MutableStateFlow<UiState<List<com.besenok.smarthome.domain.model.Device>>>(UiState.Loading)
    val devicesState: StateFlow<UiState<List<com.besenok.smarthome.domain.model.Device>>> = _devicesState.asStateFlow()

    private val _weatherState = MutableStateFlow<UiState<WeatherInfo>>(UiState.Loading)
    val weatherState: StateFlow<UiState<WeatherInfo>> = _weatherState.asStateFlow()

    private val _forecastState = MutableStateFlow<UiState<List<WeatherInfo.ForecastEntry>>>(UiState.Success(emptyList()))
    val forecastState: StateFlow<UiState<List<WeatherInfo.ForecastEntry>>> = _forecastState.asStateFlow()

    private val _timeState = MutableStateFlow<UiState<TimeInfo>>(UiState.Loading)
    val timeState: StateFlow<UiState<TimeInfo>> = _timeState.asStateFlow()

    /** Выбранные пользователем параметры для трёх ячеек row2 (null - ячейка не настроена) */
    val dashboardSlots: StateFlow<List<String?>> = preferences.dashboardSlots
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            List(AppPreferencesDataSource.DASHBOARD_SLOT_COUNT) { null }
        )

    /** Текущие значения выбранных параметров: objectId -> значение */
    private val _parameterValues = MutableStateFlow<Map<String, UiState<Any?>>>(emptyMap())
    val parameterValues: StateFlow<Map<String, UiState<Any?>>> = _parameterValues.asStateFlow()

    /** Пользовательские имена параметров: objectId -> имя (обновляются сразу после переименования) */
    val parameterNames: StateFlow<Map<String, String>> = preferences.parameterNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            iobrokerRepository.connect()
            loadDevices()
            loadWeather() // Загрузка при старте
            loadTime()
            scheduleWeatherUpdates() // Периодическое обновление каждый час
        }

        // Перезагружаем значения при изменении набора выбранных параметров
        viewModelScope.launch {
            dashboardSlots.collect { slots -> loadParameterValues(slots) }
        }

        // Как только соединение установлено - сразу подгружаем значения параметров
        viewModelScope.launch {
            iobrokerRepository.connectionState.collect { state ->
                if (state is com.besenok.smarthome.domain.repository.ConnectionState.Connected) {
                    loadParameterValues(dashboardSlots.value)
                }
            }
        }

        // Периодическое обновление значений параметров на dashboard
        viewModelScope.launch {
            while (true) {
                delay(PARAMETER_REFRESH_INTERVAL_MS)
                loadParameterValues(dashboardSlots.value)
            }
        }

        // После переименования параметра перезагружаем устройства,
        // чтобы восстановить оригинальные имена при сбросе переопределения
        viewModelScope.launch {
            preferences.parameterNames.drop(1).collect { loadDevices() }
        }
    }

    private fun loadParameterValues(slots: List<String?>) {
        val objectIds = slots.filterNotNull().distinct()
        Log.d(TAG, "loadParameterValues: slots=${slots.size}, objectIds=${objectIds.size}: $objectIds")
        
        // Берём значения из уже загруженных устройств, а не делаем отдельный запрос
        val devicesResult = _devicesState.value
        val values = mutableMapOf<String, UiState<Any?>>()
        
        when (devicesResult) {
            is UiState.Success -> {
                val devices = devicesResult.data
                Log.d(TAG, "Found ${devices.size} devices, searching for ${objectIds.size} objectIds")
                
                for (objectId in objectIds) {
                    // Ищем objectId в устройствах и каналах
                    var foundValue: Any? = null
                    var found = false
                    
                    for (device in devices) {
                        // Сначала ищем в каналах
                        for (channel in device.channels) {
                            if (channel.id == objectId) {
                                foundValue = channel.state?.value
                                Log.d(TAG, "Found objectId $objectId in device ${device.name}: $foundValue")
                                found = true
                                break
                            }
                        }
                        if (found) break
                        
                        // Если objectId == id устройства, берём device.state
                        if (device.id == objectId) {
                            foundValue = device.state?.value
                            Log.d(TAG, "Found objectId $objectId as device ${device.name}: $foundValue")
                            found = true
                            break
                        }
                    }
                    
                    if (found) {
                        values[objectId] = UiState.Success(foundValue)
                    } else {
                        Log.w(TAG, "objectId $objectId not found in devices")
                        values[objectId] = UiState.Error("Параметр не найден")
                    }
                }
            }
            is UiState.Loading -> {
                Log.d(TAG, "Devices still loading, setting Loading state for all")
                objectIds.forEach { objectId ->
                    values[objectId] = UiState.Loading
                }
            }
            is UiState.Error -> {
                Log.e(TAG, "Devices error: ${devicesResult.message}")
                objectIds.forEach { objectId ->
                    values[objectId] = UiState.Error(devicesResult.message ?: "Ошибка загрузки устройств")
                }
            }
        }
        
        _parameterValues.value = values
    }

    /**
     * Назначает параметр ячейке dashboard или очищает её (objectId = null)
     */
    fun selectParameter(slotIndex: Int, objectId: String?) {
        viewModelScope.launch {
            preferences.saveDashboardSlot(slotIndex, objectId)
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            try {
                _devicesState.value = iobrokerRepository.getDevices()
            } catch (e: Exception) {
                _devicesState.value = UiState.Error(e.message ?: "Failed to load devices")
            }
        }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            try {
                val lat = 55.7558
                val lon = 37.6173
                
                // Загружаем текущую погоду
                _weatherState.value = weatherRepository.getCurrentWeather(lat, lon)
                
                // Загружаем прогноз
                Log.d(TAG, "Loading forecast for Moscow: $lat, $lon")
                val forecastResult = weatherRepository.getForecast(lat, lon)
                Log.d(TAG, "Forecast result: $forecastResult")
                _forecastState.value = forecastResult
            } catch (e: Exception) {
                Log.e(TAG, "Error loading weather", e)
                _weatherState.value = UiState.Error(e.message ?: "Failed to load weather")
            }
        }
    }

    /**
     * Периодическое обновление погоды каждый час (3600000 мс)
     */
    private fun scheduleWeatherUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(3600000) // 1 час
                Log.d(TAG, "Scheduled weather update triggered")
                loadWeather()
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
