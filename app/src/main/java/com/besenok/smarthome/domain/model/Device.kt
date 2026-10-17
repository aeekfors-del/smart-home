package com.besenok.smarthome.domain.model

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val controlType: ControlType,
    val state: State?,
    val channels: List<Channel> = emptyList(),
    val unit: String? = null,
    val icon: String? = null,
    val connectionGroup: String = "default"
) {
    enum class DeviceType {
        LIGHT, SOCKET, SENSOR, THERMOSTAT, BLIND, GATE, OTHER
    }

    data class Channel(
        val id: String,
        val name: String,
        val controlType: ControlType,
        val state: State?
    )
}

data class State(
    val value: Any?,
    val ack: Boolean,
    val timestamp: Long,
    val unit: String? = null
)
