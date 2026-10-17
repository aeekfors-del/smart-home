package com.besenok.smarthome.ui.screens.devices

import com.besenok.smarthome.domain.model.Device

/**
 * Группа устройств по типу подключения
 */
data class DeviceGroup(
    val id: String,
    val name: String,
    val devices: List<Device>,
    val isExpanded: Boolean = true
) {
    val deviceCount: Int get() = devices.size
}
