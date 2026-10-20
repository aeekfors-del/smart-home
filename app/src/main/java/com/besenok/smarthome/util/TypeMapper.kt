package com.besenok.smarthome.util

import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.Sensor
import kotlinx.serialization.json.JsonObject

/**
 * Единый класс для маппинга типов устройств и управления
 * Объединяет все варианты определения типов из IobrokerApiService и IobrokerRepositoryImpl
 */
object TypeMapper {

    // ==================== DeviceType Mapping ====================

    /**
     * Определяет тип устройства по каналам (weighted scoring)
     * Используется в DeviceClassifier
     */
    fun mapDeviceTypeFromChannels(channels: List<Device.Channel>, fallbackName: String): Device.DeviceType {
        // Реализация перенесена в DeviceClassifier
        return mapDeviceTypeFromName(fallbackName)
    }

    /**
     * Определяет тип устройства по имени (fallback)
     * Источник: IobrokerApiService.kt:424-440
     */
    fun mapDeviceTypeFromName(name: String): Device.DeviceType {
        val nameLower = name.lowercase()
        return when {
            nameLower.contains("gateway") -> Device.DeviceType.LIGHT
            nameLower.contains("sensor_ht") || nameLower.contains("motion") ||
                    nameLower.contains("magnet") || nameLower.contains("weather") -> Device.DeviceType.SENSOR
            nameLower.contains("light") || nameLower.contains("lamp") || nameLower.contains("свет") -> Device.DeviceType.LIGHT
            nameLower.contains("socket") || nameLower.contains("outlet") || nameLower.contains("розетк") -> Device.DeviceType.SOCKET
            nameLower.contains("switch") || nameLower.contains("переключ") -> Device.DeviceType.SOCKET
            nameLower.contains("curtain") || nameLower.contains("blind") || nameLower.contains("штор") -> Device.DeviceType.BLIND
            nameLower.contains("thermostat") || nameLower.contains("climate") || nameLower.contains("термо") -> Device.DeviceType.THERMOSTAT
            else -> Device.DeviceType.OTHER
        }
    }

    /**
     * Определяет тип устройства по ID и группе (adapter-specific)
     * Источник: IobrokerRepositoryImpl.kt:507-528
     */
    fun mapDeviceTypeFromId(baseId: String, groupName: String): Device.DeviceType {
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
     * Определяет тип устройства из JSON объекта (ioBroker common.type)
     * Источник: IobrokerRepositoryImpl.kt:550-570
     */
    fun mapDeviceTypeFromJson(common: JsonObject): Device.DeviceType {
        val typeString = JsonUtils.getString(common, "type")?.lowercase()
        val customType = common["custom"]?.let { custom ->
            custom as? JsonObject
        }?.get("0")?.let { customObj ->
            customObj as? JsonObject
        }?.let { customObj ->
            JsonUtils.getString(customObj, "type")
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

    // ==================== ControlType Mapping ====================

    /**
     * Определяет тип управления по role (из IobrokerApiService)
     * Источник: IobrokerApiService.kt:443-456
     */
    fun mapControlTypeFromRole(role: String): ControlType {
        val roleLower = role.lowercase()
        return when {
            roleLower.contains("illumination") || roleLower.contains("lux") || roleLower.contains("illuminance") -> ControlType.NONE
            roleLower.contains("switch") || roleLower.contains("light.on") -> ControlType.SWITCH
            roleLower.contains("dimmer") || roleLower.contains("level") -> ControlType.SLIDER
            roleLower.contains("color") || roleLower.contains("rgb") -> ControlType.COLOR_PICKER
            roleLower.contains("button") -> ControlType.NONE
            else -> ControlType.SWITCH
        }
    }

    /**
     * Определяет тип управления по role, type, name (из IobrokerRepositoryImpl)
     * Источник: IobrokerRepositoryImpl.kt ~530-545
     */
    fun mapControlTypeFromRoleExtended(role: String, type: String, name: String? = null): ControlType {
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

    /**
     * Определяет тип управления из JSON объекта (ioBroker common.role/type)
     * Источник: IobrokerRepositoryImpl.kt:572-585
     */
    fun mapControlTypeFromJson(common: JsonObject): ControlType {
        val role = JsonUtils.getString(common, "role")?.lowercase() ?: ""
        val type = JsonUtils.getString(common, "type")?.lowercase() ?: ""

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

    // ==================== SensorType Mapping ====================

    /**
     * Определяет тип датчика по role
     * Источник: IobrokerRepositoryImpl.kt:595-606
     */
    fun mapSensorType(role: String): Sensor.SensorType? {
        val roleLower = role.lowercase()

        return when {
            roleLower.contains("temperature") -> Sensor.SensorType.TEMPERATURE
            roleLower.contains("humidity") -> Sensor.SensorType.HUMIDITY
            roleLower.contains("pressure") -> Sensor.SensorType.PRESSURE
            roleLower.contains("lux") || roleLower.contains("illuminance") -> Sensor.SensorType.LIGHT
            roleLower.contains("motion") || roleLower.contains("indicator.motion") -> Sensor.SensorType.MOTION
            roleLower.contains("magnet") || (roleLower.contains("state") && roleLower.contains("opened")) -> Sensor.SensorType.DOOR_WINDOW
            else -> null
        }
    }
}
