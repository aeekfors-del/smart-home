package com.besenok.smarthome.data.remote.time.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeResponse(
    val datetime: String,
    val day_of_week: Int,
    val day_of_year: Int,
    val timestamp: Long,
    val timezone: String
)
