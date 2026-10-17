package com.besenok.smarthome.domain.model

data class TimeInfo(
    val dateTime: String,
    val timezone: String,
    val timestamp: Long,
    val dayOfWeek: String
)
