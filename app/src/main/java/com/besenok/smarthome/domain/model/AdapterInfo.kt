package com.besenok.smarthome.domain.model

data class AdapterInfo(
    val id: String,              // "mihome.0", "tuya.0"
    val name: String,            // "Mi Home", "Tuya"
    val alive: Boolean,          // true/false
    val connected: Boolean,      // true/false
    val devicesCount: Int = 0    // количество устройств
)
