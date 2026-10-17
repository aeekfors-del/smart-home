package com.besenok.smarthome.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_states")
data class DeviceStateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val controlType: String,
    val value: String?,
    val ack: Boolean,
    val timestamp: Long,
    val unit: String?
)
