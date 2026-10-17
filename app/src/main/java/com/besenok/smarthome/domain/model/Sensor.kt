package com.besenok.smarthome.domain.model

data class Sensor(
    val id: String,
    val name: String,
    val sensorType: SensorType,
    val value: Any?,
    val unit: String,
    val timestamp: Long
) {
    enum class SensorType {
        TEMPERATURE, HUMIDITY, PRESSURE, LIGHT, MOTION, DOOR_WINDOW, GAS, SMOKE
    }
}
