package com.besenok.smarthome.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.besenok.smarthome.data.local.room.dao.DeviceStateDao
import com.besenok.smarthome.data.local.room.dao.ScenarioDao
import com.besenok.smarthome.data.local.room.entity.DeviceStateEntity
import com.besenok.smarthome.data.local.room.entity.ScenarioEntity

@Database(
    entities = [DeviceStateEntity::class, ScenarioEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SmartHomeDatabase : RoomDatabase() {
    abstract fun deviceStateDao(): DeviceStateDao
    abstract fun scenarioDao(): ScenarioDao
}
