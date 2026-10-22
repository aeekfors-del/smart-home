package com.besenok.smarthome.domain.repository

import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.Sensor
import com.besenok.smarthome.domain.model.Scenario
import com.besenok.smarthome.ui.common.UiState
import kotlinx.coroutines.flow.StateFlow

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val sessionId: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

interface IobrokerRepository {
    suspend fun connect()
    suspend fun disconnect()
    val connectionState: StateFlow<ConnectionState>

    suspend fun getDevices(): UiState<List<Device>>
    suspend fun getDevice(deviceId: String): UiState<Device>

    /**
     * Значение одного состояния ioBroker (параметра устройства)
     * @param objectId полный ID состояния, например "mihome.0.devices.sensor_ht_1234.temperature"
     */
    suspend fun getState(objectId: String): UiState<Any?>

    suspend fun setState(objectId: String, value: Any, ack: Boolean = true): UiState<Unit>

    suspend fun getAdapters(): UiState<List<AdapterInfo>>
    
    /**
     * Получение устройств из адаптера
     * @param adapterId ID адаптера (например, "mihome.0", "tuya.0")
     */
    suspend fun getDevicesFromAdapter(adapterId: String): UiState<List<Device>>
    
    suspend fun getSensors(): UiState<List<Sensor>>
    suspend fun getScenarios(): UiState<List<Scenario>>
    suspend fun executeScenario(scenarioId: String): UiState<Unit>
}
