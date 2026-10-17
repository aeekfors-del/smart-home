package com.besenok.smarthome.data.repository

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.data.remote.iobroker.IobrokerApiService
import com.besenok.smarthome.data.remote.iobroker.IobrokerWebSocketClient
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.Sensor
import com.besenok.smarthome.domain.model.Scenario
import com.besenok.smarthome.domain.model.State
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.domain.repository.ConnectionState
import com.besenok.smarthome.ui.common.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IobrokerRepositoryImpl"

@Singleton
class IobrokerRepositoryImpl @Inject constructor(
    private val apiService: IobrokerApiService,
    private val webSocketClient: IobrokerWebSocketClient,
    private val settingsDataSource: AppPreferencesDataSource
) : IobrokerRepository {

    override val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    override suspend fun connect() {
        webSocketClient.connect()
    }

    override suspend fun disconnect() {
        webSocketClient.disconnect()
    }

    override suspend fun getDevices(): UiState<List<Device>> {
        return try {
            Log.d(TAG, "Getting devices from ioBroker...")
            val devices = parseDevicesFromObjects()
            Log.d(TAG, "Parsed ${devices.size} devices: $devices")
            UiState.Success(devices)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices", e)
            UiState.Error(e.message ?: "Ошибка получения устройств")
        }
    }

    private suspend fun parseDevicesFromObjects(): List<Device> {
        val allDevices = mutableListOf<Device>()
        
        // Парсим mihome.0 (устройства в mihome.0.devices.*)
        val mihomeDevices = parseGroupDevices("mihome.0.devices.*", "mihome.0")
        allDevices.addAll(mihomeDevices)
        Log.d(TAG, "Parsed ${mihomeDevices.size} devices from mihome.0")
        
        // Парсим tuya.0 (устройства в tuya.0.*)
        val tuyaDevices = parseGroupDevices("tuya.0.*", "tuya.0")
        allDevices.addAll(tuyaDevices)
        Log.d(TAG, "Parsed ${tuyaDevices.size} devices from tuya.0")
        
        Log.d(TAG, "Total parsed devices: ${allDevices.size}")
        return allDevices
    }
    
    /**
     * Парсит устройства из группы (mihome.0, tuya.0, и т.д.)
     */
    private suspend fun parseGroupDevices(
        pattern: String,
        groupName: String
    ): List<Device> {
        // Получаем все объекты из группы
        val allObjects = apiService.getObjectTree(pattern)
        Log.d(TAG, "Got ${allObjects.size} objects from $pattern")
        
        // Группируем devices по базовому ID устройства
        val devicesMap = mutableMapOf<String, MutableList<JsonObject>>()
        
        for (obj in allObjects) {
            val deviceId = getJsonString(obj, "_id") ?: continue
            
            // Проверяем, что это state из нужной группы
            if (!deviceId.startsWith("$pattern".removeSuffix(".*"))) continue
            
            // Извлекаем базовый ID устройства
            val baseDeviceId = extractBaseDeviceId(deviceId)
            if (baseDeviceId == null) {
                Log.w(TAG, "Could not extract base device ID from: $deviceId")
                continue
            }
            
            devicesMap.getOrPut(baseDeviceId) { mutableListOf() }.add(obj)
        }
        
        Log.d(TAG, "Found ${devicesMap.size} unique devices in $groupName")
        
        // Получаем все состояния для получения значений
        val allStates = apiService.getStates(pattern)
        Log.d(TAG, "Got ${allStates?.keys?.size} states from $pattern")
        
        // Парсим устройства
        val devices = mutableListOf<Device>()
        for ((baseId, stateObjects) in devicesMap) {
            val device = parseGroupDevice(baseId, stateObjects, allStates ?: emptyMap(), groupName)
            if (device != null) {
                devices.add(device)
                Log.d(TAG, "Parsed device: ${device.name} (${device.type}) with ${device.channels.size} channels")
            }
        }
        
        return devices
    }
    
    /**
     * Извлекает базовый ID устройства из полного ID state
     */
    private fun extractBaseDeviceId(fullId: String): String? {
        val parts = fullId.split(".")
        
        // Для Tuya: tuya.0.deviceId.stateName (минимум 4 части)
        if (fullId.startsWith("tuya.0.")) {
            if (parts.size < 4) return null
            // Берём первые 3 части: tuya.0.deviceId
            return parts.take(3).joinToString(".")
        }
        
        // Для mihome: mihome.0.devices.deviceType_id.stateName (минимум 5 частей)
        if (fullId.startsWith("mihome.0.devices.")) {
            if (parts.size < 5) return null
            // Берём первые 4 части: mihome.0.devices.deviceId
            return parts.take(4).joinToString(".")
        }
        
        return null
    }
    
    /**
     * Парсит устройство из группы (mihome.0, tuya.0, и т.д.)
     */
    private fun parseGroupDevice(
        baseId: String,
        stateObjects: List<JsonObject>,
        allStates: Map<String, Any>,
        groupName: String
    ): Device? {
        if (stateObjects.isEmpty()) return null
        
        // Используем читаемое имя из baseId
        val deviceName = extractDeviceName(baseId, groupName)
        
        // Определяем тип устройства по ID
        val deviceType = mapDeviceType(baseId, groupName)
        
        // Парсим состояния устройства как channels
        val channels = mutableListOf<Device.Channel>()
        for (stateObj in stateObjects) {
            val stateId = getJsonString(stateObj, "_id") ?: continue
            
            // Ищем это состояние в states для получения значения
            val stateData = allStates[stateId] as? JsonObject
            
            val stateCommon = stateObj["common"] as? JsonObject ?: continue
            val stateName = getJsonString(stateCommon, "name") ?: getJsonString(stateCommon, "role") ?: ""
            val stateRole = getJsonString(stateCommon, "role") ?: ""
            val stateType = getJsonString(stateCommon, "type") ?: ""
            
            val controlType = mapControlType(stateRole, stateType, stateName)
            
            // Получаем текущее значение из stateData (val, ack, ts)
            val value = stateData?.get("val")?.toString()?.let { parseJsonValue(it) }
            val ack = stateData?.get("ack")?.toString()?.toBoolean() ?: false
            val ts = stateData?.get("ts")?.toString()?.toLongOrNull() ?: 0
            val unit = getJsonString(stateCommon, "unit") ?: ""
            
            Log.d(TAG, "Channel $stateId: name=$stateName, value=$value, unit=$unit")
            
            // Проверяем controlType по имени и ID канала
            val channelControlType = if (stateId.contains("rgb", ignoreCase = true)) {
                ControlType.COLOR_PICKER
            } else {
                mapControlType(stateRole, stateType, stateName)
            }
            
            channels.add(
                Device.Channel(
                    id = stateId,
                    name = if (stateName.isNotEmpty()) stateName else stateId.substringAfterLast("."),
                    controlType = channelControlType,
                    state = State(
                        value = value,
                        ack = ack,
                        timestamp = ts,
                        unit = unit
                    )
                )
            )
        }
        
        // Получаем текущее состояние устройства из первого channel с значением
        val deviceState = channels.firstOrNull { it.state != null && it.state!!.value != null }?.state
        
        return Device(
            id = baseId,
            name = deviceName,
            type = deviceType,
            controlType = when (deviceType) {
                Device.DeviceType.LIGHT -> ControlType.SWITCH
                Device.DeviceType.SENSOR -> ControlType.NONE
                else -> ControlType.NONE
            },
            state = deviceState,
            channels = channels,
            unit = null,
            icon = null,
            connectionGroup = groupName
        )
    }
    
    /**
     * Извлекает читаемое имя устройства из baseId
     */
    private fun extractDeviceName(baseId: String, groupName: String): String {
        // Убираем префикс группы (mihome.0.devices. или tuya.0.)
        val deviceId = when (groupName) {
            "mihome.0" -> baseId.substringAfter("mihome.0.devices.")
            "tuya.0" -> baseId.substringAfter("tuya.0.")
            else -> baseId.substringAfterLast(".")
        }
        
        // Определяем тип устройства по ID
        val deviceType = when (groupName) {
            "mihome.0" -> mapMiHomeDeviceTypeName(deviceId)
            "tuya.0" -> mapTuyaDeviceTypeName(deviceId)
            else -> deviceId
        }
        
        // Для Tuya: используем тип устройства + первые 6 символов ID
        if (groupName == "tuya.0") {
            // Убираем суффиксы типа .1, .2, -0, -1 и т.д.
            val cleanDeviceId = deviceId.split(".")[0].split("-")[0]
            val shortId = if (cleanDeviceId.length > 6) cleanDeviceId.take(6) else cleanDeviceId
            // Если deviceType не определён (fallback), используем "Устройство"
            val typeName = if (deviceType == deviceId) "Устройство" else deviceType
            return "$typeName $shortId"
        }
        
        // Для mihome: извлекаем ID (последние символы после _)
        val idPart = deviceId.substringAfter("_")
        val shortId = if (idPart.isNotEmpty() && idPart != deviceId) idPart else deviceId.takeLast(6)
        return "$deviceType $shortId"
    }
    
    private fun mapMiHomeDeviceTypeName(deviceId: String): String {
        return when {
            deviceId.startsWith("gateway") -> "Gateway"
            deviceId.startsWith("sensor_ht") -> "Температура"
            deviceId.startsWith("motion") -> "Датчик движения"
            deviceId.startsWith("magnet") -> "Датчик открытия"
            deviceId.startsWith("weather") -> "Погода"
            deviceId.startsWith("switch") -> "Кнопка"
            else -> deviceId
        }
    }
    
    private fun mapTuyaDeviceTypeName(deviceId: String): String {
        return when {
            deviceId.contains("light") || deviceId.contains("lamp") -> "Свет"
            deviceId.contains("socket") || deviceId.contains("outlet") -> "Розетка"
            deviceId.contains("switch") -> "Переключатель"
            deviceId.contains("sensor") -> "Датчик"
            deviceId.contains("thermostat") || deviceId.contains("temp") -> "Термостат"
            deviceId.contains("curtain") || deviceId.contains("blind") -> "Штора"
            deviceId.contains("camera") -> "Камера"
            deviceId.contains("fan") -> "Вентилятор"
            else -> deviceId
        }
    }
    
    /**
     * Определяет тип устройства
     */
    private fun mapDeviceType(baseId: String, groupName: String): Device.DeviceType {
        return when (groupName) {
            "mihome.0" -> mapMiHomeDeviceType(baseId)
            "tuya.0" -> mapTuyaDeviceType(baseId)
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapMiHomeDeviceType(baseId: String): Device.DeviceType {
        return when {
            baseId.contains("gateway") -> Device.DeviceType.LIGHT
            baseId.contains("sensor_ht") -> Device.DeviceType.SENSOR
            baseId.contains("motion") -> Device.DeviceType.SENSOR
            baseId.contains("magnet") -> Device.DeviceType.SENSOR
            baseId.contains("weather_v1") || baseId.contains("weather") -> Device.DeviceType.SENSOR
            baseId.contains("switch") -> Device.DeviceType.OTHER
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapTuyaDeviceType(baseId: String): Device.DeviceType {
        return when {
            baseId.contains("light") || baseId.contains("lamp") -> Device.DeviceType.LIGHT
            baseId.contains("socket") || baseId.contains("outlet") -> Device.DeviceType.SOCKET
            baseId.contains("switch") -> Device.DeviceType.SOCKET
            baseId.contains("sensor") -> Device.DeviceType.SENSOR
            baseId.contains("thermostat") || baseId.contains("temp") -> Device.DeviceType.THERMOSTAT
            baseId.contains("curtain") || baseId.contains("blind") -> Device.DeviceType.BLIND
            baseId.contains("fan") -> Device.DeviceType.OTHER
            else -> Device.DeviceType.OTHER
        }
    }
    
    /**
     * Определяет тип управления
     */
    private fun mapControlType(role: String, type: String, name: String? = null): ControlType {
        val roleLower = role.lowercase()
        val nameLower = name?.lowercase() ?: ""
        return when {
            roleLower.contains("illumination") || roleLower.contains("lux") || roleLower.contains("illuminance") -> ControlType.NONE
            roleLower.contains("switch") || roleLower.contains("light.on") -> ControlType.SWITCH
            roleLower.contains("dimmer") || roleLower.contains("level") -> ControlType.SLIDER
            roleLower.contains("color") || roleLower.contains("rgb") || nameLower.contains("rgb") -> ControlType.COLOR_PICKER
            roleLower.contains("button") -> ControlType.NONE
            else -> ControlType.SWITCH
        }
    }

    private suspend fun parseDevice(obj: JsonObject): Device? {
        val common = obj["common"] as? JsonObject ?: return null
        val type = obj["type"]?.toString() ?: return null
        
        // Only parse devices and channels
        if (type != "device" && type != "channel") return null
        
        val deviceId = obj["_id"]?.toString() ?: return null
        val deviceName = getJsonString(common, "name") ?: deviceId
        
        // Determine device type from ioBroker type
        val deviceType = mapToDeviceType(common)
        val controlType = mapToControlType(common)
        
        // Find state
        var deviceState: State? = null
        var icon = getJsonString(common, "icon")
        
        // Try to get state from members
        val membersStr = common["members"]?.toString()
        if (!membersStr.isNullOrEmpty()) {
            try {
                val memberId = membersStr.trim('[', ']', ' ', '"')
                if (memberId.isNotEmpty()) {
                    deviceState = getStateSync(memberId)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // If no state from members, try to find state child object
        if (deviceState == null) {
            val stateObj = findStateChildSync(deviceId)
            if (stateObj != null) {
                deviceState = parseStateObject(stateObj)
                icon = getJsonString(stateObj["common"] as? JsonObject, "icon") ?: icon
            }
        }
        
        // Parse channels if this is a device
        val channels = if (type == "device") {
            parseChannelsSync(deviceId)
        } else {
            emptyList()
        }
        
        val unit = getJsonString(common, "unit")
        val unitInState = deviceState?.unit ?: unit
        
        return Device(
            id = deviceId,
            name = deviceName,
            type = deviceType,
            controlType = controlType,
            state = deviceState?.copy(unit = unitInState),
            channels = channels,
            unit = unit,
            icon = icon
        )
    }
    
    private suspend fun parseChannelsSync(deviceId: String): List<Device.Channel> {
        val channels = mutableListOf<Device.Channel>()
        
        try {
            val channelPattern = "$deviceId.*"
            val channelObjects = apiService.getObjectTree(channelPattern)
            
            for (channelObj in channelObjects) {
                val channel = parseChannelSync(channelObj)
                if (channel != null) {
                    channels.add(channel)
                }
            }
        } catch (e: Exception) {
            // Ignore channel parsing errors
        }
        
        return channels
    }
    
    private suspend fun parseChannelSync(obj: JsonObject): Device.Channel? {
        val common = obj["common"] as? JsonObject ?: return null
        val type = obj["type"]?.toString() ?: return null
        
        if (type != "channel") return null
        
        val channelId = obj["_id"]?.toString() ?: return null
        val channelName = getJsonString(common, "name") ?: channelId
        
        val controlType = mapToControlType(common)
        
        // Try to find state
        var channelState: State? = null
        val membersStr = common["members"]?.toString()
        if (!membersStr.isNullOrEmpty()) {
            try {
                val memberId = membersStr.trim('[', ']', ' ', '"')
                if (memberId.isNotEmpty()) {
                    channelState = getStateSync(memberId)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        if (channelState == null) {
            val stateObj = findStateChildSync(channelId)
            if (stateObj != null) {
                channelState = parseStateObject(stateObj)
            }
        }
        
        return Device.Channel(
            id = channelId,
            name = channelName,
            controlType = controlType,
            state = channelState
        )
    }
    
    private suspend fun findStateChildSync(deviceId: String): JsonObject? {
        try {
            val statePattern = "$deviceId.0" // Typical state ID pattern
            val stateObjects = apiService.getObjectTree(statePattern)
            return stateObjects.firstOrNull()
        } catch (e: Exception) {
            return null
        }
    }
    
    private suspend fun getStateSync(objectId: String): State? {
        return try {
            val iobrokerState = apiService.getState(objectId)
            if (iobrokerState != null) {
                State(
                    value = iobrokerState.value,
                    ack = iobrokerState.ack,
                    timestamp = iobrokerState.ts.toLong(),
                    unit = null
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseStateObject(obj: JsonObject): State {
        val common = obj["common"] as? JsonObject ?: JsonObject(emptyMap())
        
        val rawValue = obj["val"]
        val value = parseJsonValue(rawValue?.toString())
        
        val ack = getJsonString(obj, "ack")?.toBoolean() ?: false
        val ts = getJsonString(obj, "ts")?.toLongOrNull() ?: 0
        val unit = getJsonString(common, "unit")
        
        return State(
            value = value,
            ack = ack,
            timestamp = ts,
            unit = unit
        )
    }
    
    private fun parseJsonValue(str: String?): Any? {
        if (str == null) return null
        return when (str) {
            "true" -> true
            "false" -> false
            else -> try {
                str.toInt()
            } catch (e: NumberFormatException) {
                try {
                    str.toDouble()
                } catch (e: NumberFormatException) {
                    str
                }
            }
        }
    }
    
    private fun mapToDeviceType(common: JsonObject): Device.DeviceType {
        val typeString = getJsonString(common, "type")?.lowercase()
        val customType = common["custom"]?.let { custom ->
            custom as? JsonObject
        }?.get("0")?.let { customObj ->
            customObj as? JsonObject
        }?.let { customObj ->
            getJsonString(customObj, "type")
        }?.lowercase()
        
        return when (customType ?: typeString) {
            "light", "lightbulb", "lamp" -> Device.DeviceType.LIGHT
            "socket", "outlet", "receptacle" -> Device.DeviceType.SOCKET
            "thermostat", "climate", "heater" -> Device.DeviceType.THERMOSTAT
            "blind", "shutter", "curtain" -> Device.DeviceType.BLIND
            "gate", "door", "garage" -> Device.DeviceType.GATE
            "sensor", "weather", "meter" -> Device.DeviceType.SENSOR
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapToControlType(common: JsonObject): ControlType {
        val role = getJsonString(common, "role")?.lowercase() ?: ""
        val type = getJsonString(common, "type")?.lowercase() ?: ""
        
        return when {
            role.contains("dim.level") || role.contains("light.on") -> ControlType.SLIDER
            role.contains("blinds") || role.contains("shutter") -> ControlType.SLIDER
            type == "number" || type == "integer" -> ControlType.NUMBER_INPUT
            type == "string" || type == "text" -> ControlType.TEXT_INPUT
            type == "boolean" || role.contains("switch") || role.contains("power") -> ControlType.SWITCH
            type == "number" && role.contains("heat") -> ControlType.SLIDER
            else -> ControlType.SWITCH
        }
    }
    
    private fun getJsonString(obj: JsonObject?, key: String): String? {
        return obj?.get(key)?.toString()?.trim('"')
    }

    override suspend fun getDevice(deviceId: String): UiState<Device> {
        return try {
            Log.d(TAG, "Getting device $deviceId from mihome.0")
            
            // Получаем все state объекты для этого устройства
            val pattern = "mihome.0.devices.*"
            val allStates = apiService.getStates(pattern)
            
            val deviceStates = allStates?.entries?.filterNotNull()?.filter { (stateId, _) ->
                stateId.contains(deviceId.split(".").take(4).joinToString("."))
            }?.associate { (stateId, stateObj) ->
                stateId to (stateObj as? JsonObject)
            } ?: emptyMap()
            
            Log.d(TAG, "Found ${deviceStates.size} states for device $deviceId")
            
            // Создаем устройство с загруженными состояниями
            val device = Device(
                id = deviceId,
                name = deviceId.split(".").lastOrNull() ?: deviceId,
                type = mapMiHomeDeviceType(deviceId),
                controlType = ControlType.SWITCH,
                state = null,
                channels = deviceStates.mapNotNull { (stateId, stateObj) ->
                    val common = stateObj?.get("common") as? JsonObject ?: return@mapNotNull null
                    val role = getJsonString(common, "role") ?: ""
                    
                    // Пропускаем battery
                    if (role.contains("battery")) return@mapNotNull null
                    
                    val value = stateObj["val"]?.toString()?.let { parseJsonValue(it) }
                    val unit = getJsonString(common, "unit") ?: ""
                    val name = getJsonString(common, "name") ?: stateId
                    val ts = stateObj["ts"]?.toString()?.toLongOrNull() ?: 0
                    
                    Device.Channel(
                        id = stateId,
                        name = name,
                        controlType = mapControlType(role, getJsonString(common, "type") ?: ""),
                        state = State(
                            value = value,
                            ack = stateObj["ack"]?.toString()?.toBoolean() ?: false,
                            timestamp = ts,
                            unit = unit
                        )
                    )
                },
                unit = null,
                icon = null
            )
            
            UiState.Success(device)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device $deviceId", e)
            UiState.Error(e.message ?: "Ошибка получения устройства")
        }
    }

    override suspend fun setState(objectId: String, value: Any, ack: Boolean): UiState<Unit> {
        return try {
            Log.d(TAG, "Setting state $objectId = $value (ack=$ack)")
            // Используем /set/ endpoint вместо /json/set/
            apiService.setState(objectId, value)
            Log.d(TAG, "State $objectId set successfully")
            UiState.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting state $objectId", e)
            UiState.Error(e.message ?: "Failed to set state")
        }
    }

    override suspend fun getSensors(): UiState<List<Sensor>> {
        return try {
            // Получаем все state объекты из mihome.0
            val allStates = apiService.getStates("mihome.0.devices.*")
            Log.d(TAG, "Got states from mihome.0: ${allStates?.keys?.size}")
            
            val sensors = mutableListOf<Sensor>()
            
            if (allStates != null) {
                for ((stateId, stateObj) in allStates) {
                    val stateData = stateObj as? JsonObject ?: continue
                    val common = stateData["common"] as? JsonObject ?: continue
                    
                    val role = getJsonString(common, "role") ?: ""
                    val sensorType = mapMiHomeSensorType(role)
                    
                    // Пропускаем battery и non-sensor states
                    if (sensorType == null || role.contains("battery")) continue
                    
                    // Получаем текущее значение
                    val value = stateData["val"]?.toString()?.let { parseJsonValue(it) }
                    val unit = getJsonString(common, "unit") ?: ""
                    val name = getJsonString(common, "name") ?: stateId
                    val ts = stateData["ts"]?.toString()?.toLongOrNull() ?: 0
                    
                    sensors.add(
                        Sensor(
                            id = stateId,
                            name = name,
                            sensorType = sensorType,
                            value = value,
                            unit = unit,
                            timestamp = ts
                        )
                    )
                }
            }
            
            Log.d(TAG, "Parsed ${sensors.size} sensors from mihome.0")
            UiState.Success(sensors)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sensors", e)
            UiState.Error(e.message ?: "Ошибка получения датчиков")
        }
    }
    
    /**
     * Определяет тип датчика mihome по role
     */
    private fun mapMiHomeSensorType(role: String): Sensor.SensorType? {
        val roleLower = role.lowercase()
        
        return when {
            roleLower.contains("temperature") -> Sensor.SensorType.TEMPERATURE
            roleLower.contains("humidity") -> Sensor.SensorType.HUMIDITY
            roleLower.contains("pressure") -> Sensor.SensorType.PRESSURE
            roleLower.contains("lux") || roleLower.contains("illuminance") -> Sensor.SensorType.LIGHT
            roleLower.contains("motion") || roleLower.contains("indicator.motion") -> Sensor.SensorType.MOTION
            roleLower.contains("magnet") || roleLower.contains("state") && roleLower.contains("opened") -> Sensor.SensorType.DOOR_WINDOW
            else -> null
        }
    }

    override suspend fun getScenarios(): UiState<List<Scenario>> {
        // В mihome.0 нет традиционных сценариев
        // Можно добавить сценарии позже через ioBroker adapter scenarios
        return UiState.Success(emptyList())
    }

    override suspend fun executeScenario(scenarioId: String): UiState<Unit> {
        return UiState.Success(Unit)
    }
}
