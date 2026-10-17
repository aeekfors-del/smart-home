package com.besenok.smarthome.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val trigger: String?,
    val actions: String,
    val enabled: Boolean,
    val lastExecuted: Long?
)
