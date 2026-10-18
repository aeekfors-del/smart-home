package com.besenok.smarthome.data.repository

import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.data.local.room.dao.DeviceStateDao
import com.besenok.smarthome.data.local.room.dao.ScenarioDao
import com.besenok.smarthome.data.remote.iobroker.IobrokerApiService
import com.besenok.smarthome.data.remote.iobroker.IobrokerWebSocketClient
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.Sensor
import com.besenok.smarthome.domain.model.Scenario
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.domain.repository.ConnectionState
import com.besenok.smarthome.ui.common.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock репозиторий для тестирования без реального ioBroker.
 * Используется при запуске в режиме DEBUG на эмуляторе или ПК.
 */
@Singleton
class IobrokerRepositoryMock @Inject constructor(
    private val deviceStateDao: DeviceStateDao,
    private val scenarioDao: ScenarioDao
) : IobrokerRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("mock-session"))
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Mock данные устройств
    private val mockDevices = listOf(
        Device(
            id = "mock.light.1",
            name = "Лампа в гостиной",
            type = Device.DeviceType.LIGHT,
            controlType = ControlType.SWITCH,
            state = com.besenok.smarthome.domain.model.State(value = true, ack = false, timestamp = System.currentTimeMillis())
        ),
        Device(
            id = "mock.light.2",
            name = "Свет на кухне",
            type = Device.DeviceType.LIGHT,
            controlType = ControlType.SLIDER,
            state = com.besenok.smarthome.domain.model.State(value = 75, ack = false, timestamp = System.currentTimeMillis())
        ),
        Device(
            id = "mock.socket.1",
            name = "Розетка у телевизора",
            type = Device.DeviceType.SOCKET,
            controlType = ControlType.SWITCH,
            state = com.besenok.smarthome.domain.model.State(value = false, ack = false, timestamp = System.currentTimeMillis())
        ),
        Device(
            id = "mock.sensor.1",
            name = "Датчик температуры",
            type = Device.DeviceType.SENSOR,
            controlType = ControlType.NONE,
            state = com.besenok.smarthome.domain.model.State(value = 22.5, ack = false, timestamp = System.currentTimeMillis())
        )
    )

    // Mock данные датчиков
    private val mockSensors = listOf(
        Sensor(
            id = "mock.sensor.temp",
            name = "Температура",
            sensorType = Sensor.SensorType.TEMPERATURE,
            value = 22.5,
            unit = "°C",
            timestamp = System.currentTimeMillis()
        ),
        Sensor(
            id = "mock.sensor.humidity",
            name = "Влажность",
            sensorType = Sensor.SensorType.HUMIDITY,
            value = 45,
            unit = "%",
            timestamp = System.currentTimeMillis()
        ),
        Sensor(
            id = "mock.sensor.pressure",
            name = "Давление",
            sensorType = Sensor.SensorType.PRESSURE,
            value = 760,
            unit = "mmHg",
            timestamp = System.currentTimeMillis()
        ),
        Sensor(
            id = "mock.sensor.motion",
            name = "Датчик движения",
            sensorType = Sensor.SensorType.MOTION,
            value = false,
            unit = "",
            timestamp = System.currentTimeMillis()
        )
    )

    // Mock данные сценариев
    private val mockScenarios = listOf(
        Scenario(
            id = "mock.scenario.1",
            name = "Доброе утро",
            description = "Включить свет, открыть шторы, включить кофе",
            trigger = "TIME:07:00",
            actions = listOf("light.toggle", "blind.open", "coffee.start"),
            enabled = true
        ),
        Scenario(
            id = "mock.scenario.2",
            name = "Спокойной ночи",
            description = "Выключить свет, закрыть шторы, включить охрану",
            trigger = "TIME:23:00",
            actions = listOf("light.off", "blind.close", "security.arm"),
            enabled = false
        ),
        Scenario(
            id = "mock.scenario.3",
            name = "Уход из дома",
            description = "Выключить все устройства, включить охрану",
            trigger = "MANUAL",
            actions = listOf("light.off", "socket.off", "security.arm"),
            enabled = true
        )
    )

    override suspend fun connect() {
        // Mock подключение мгновенное
        _connectionState.value = ConnectionState.Connected("mock-session")
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun getDevices(): UiState<List<Device>> {
        // Имитируем задержку сети
        delay(500)
        return UiState.Success(mockDevices)
    }

    override suspend fun getDevice(deviceId: String): UiState<Device> {
        delay(300)
        return mockDevices.find { it.id == deviceId }
            ?.let { UiState.Success(it) }
            ?: UiState.Error("Device not found")
    }

    override suspend fun setState(objectId: String, value: Any, ack: Boolean): UiState<Unit> {
        // Mock — просто успешный ответ
        delay(200)
        return UiState.Success(Unit)
    }

    override suspend fun getAdapters(): UiState<List<JsonObject>> {
        // Mock - возвращаем пустой список адаптеров
        delay(300)
        return UiState.Success(emptyList())
    }

    override suspend fun getDevicesFromAdapter(adapterId: String): UiState<List<Device>> {
        // Mock - возвращаем тестовые устройства
        delay(500)
        val mockDevices = listOf(
            Device(
                id = "mock.$adapterId.device.1",
                name = "Устройство 1 из $adapterId",
                type = Device.DeviceType.LIGHT,
                controlType = ControlType.SWITCH,
                state = com.besenok.smarthome.domain.model.State(value = true, ack = false, timestamp = System.currentTimeMillis())
            ),
            Device(
                id = "mock.$adapterId.device.2",
                name = "Устройство 2 из $adapterId",
                type = Device.DeviceType.SOCKET,
                controlType = ControlType.SWITCH,
                state = com.besenok.smarthome.domain.model.State(value = false, ack = false, timestamp = System.currentTimeMillis())
            )
        )
        return UiState.Success(mockDevices)
    }

    override suspend fun getSensors(): UiState<List<Sensor>> {
        delay(500)
        return UiState.Success(mockSensors)
    }

    override suspend fun getScenarios(): UiState<List<Scenario>> {
        delay(500)
        return UiState.Success(mockScenarios)
    }

    override suspend fun executeScenario(scenarioId: String): UiState<Unit> {
        delay(300)
        return UiState.Success(Unit)
    }
}
