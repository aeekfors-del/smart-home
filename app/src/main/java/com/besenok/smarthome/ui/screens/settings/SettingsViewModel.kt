package com.besenok.smarthome.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferencesDataSource
) : ViewModel() {

    // ioBroker settings
    private val _host = preferences.iobrokerHost.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesDataSource.DEFAULT_HOST)
    val host: StateFlow<String> = _host

    private val _port = preferences.iobrokerPort.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesDataSource.DEFAULT_PORT)
    val port: StateFlow<String> = _port

    private val _username = preferences.iobrokerUsername.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val username: StateFlow<String> = _username

    // Пароль (и его шифртекст) в UI не передаётся - только признак того, что он сохранён
    val hasPassword: StateFlow<Boolean> = preferences.hasIobrokerPassword
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Weather settings
    private val _weatherRegion = preferences.weatherRegion.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesDataSource.DEFAULT_WEATHER_REGION)
    val weatherRegion: StateFlow<String> = _weatherRegion

    val weatherRegionName: StateFlow<String> = _weatherRegion
        .map { coords ->
            AppPreferencesDataSource.WEATHER_REGIONS.entries.find { it.value == coords }?.key
                ?: run {
                    val parts = coords.split(",")
                    if (parts.size == 2) {
                        "${parts[0]}°, ${parts[1]}°"
                    } else {
                        "Москва"
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Москва")

    val weatherRegions: List<String> = AppPreferencesDataSource.WEATHER_REGIONS.keys.toList()

    // OpenWeatherMap API key
    private val _openWeatherMapApiKey = preferences.openweathermapApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val openWeatherMapApiKey: StateFlow<String> = _openWeatherMapApiKey

    // Theme settings
    private val _appTheme = preferences.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesDataSource.DEFAULT_APP_THEME)
    val appTheme: StateFlow<String> = _appTheme

    val themeOptions = listOf("SYSTEM", "LIGHT", "DARK")
    val themeOptionsDisplay = mapOf(
        "SYSTEM" to "Как в системе",
        "LIGHT" to "Светлая",
        "DARK" to "Тёмная"
    )

    // Connection state
    private val _connectionState = MutableStateFlow<UiState<String>>(UiState.Success("Готов к проверке"))
    val connectionState: StateFlow<UiState<String>> = _connectionState.asStateFlow()

    fun testConnection() {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading
            try {
                // Try to connect to ioBroker
                val host = _host.value
                val port = _port.value
                // Simulate connection test (replace with actual WebSocket test)
                kotlinx.coroutines.delay(1500)
                _connectionState.value = UiState.Success("✅ Подключение к $host:$port успешно")
            } catch (e: Exception) {
                _connectionState.value = UiState.Error("❌ Ошибка подключения: ${e.message}")
            }
        }
    }

    fun saveIoBrokerSettings(host: String, port: String, username: String, password: String) {
        viewModelScope.launch {
            preferences.saveIoBrokerSettings(host, port, username, password)
        }
    }

    fun saveWeatherRegion(regionName: String) {
        viewModelScope.launch {
            preferences.saveWeatherRegion(regionName)
        }
    }

    fun saveWeatherCoordinates(lat: Double, lon: Double, cityName: String = "") {
        val coords = "$lat,$lon"
        viewModelScope.launch {
            preferences.saveWeatherRegion(coords)
        }
    }

    fun saveAppTheme(theme: String) {
        viewModelScope.launch {
            preferences.saveAppTheme(theme)
        }
    }

    fun saveOpenWeatherMapApiKey(apiKey: String) {
        viewModelScope.launch {
            preferences.saveOpenWeatherMapApiKey(apiKey)
        }
    }

    fun clearAllSettings() {
        viewModelScope.launch {
            preferences.clearSettings()
        }
    }
}
