package com.besenok.smarthome.data.local.room.dao

import androidx.room.*
import com.besenok.smarthome.data.local.room.entity.DeviceStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStateDao {

    @Query("SELECT * FROM device_states")
    fun getAllStates(): Flow<List<DeviceStateEntity>>

    @Query("SELECT * FROM device_states WHERE id = :id")
    suspend fun getStateById(id: String): DeviceStateEntity?

    @Upsert
    suspend fun upsertState(state: DeviceStateEntity)

    @Delete
    suspend fun deleteState(state: DeviceStateEntity)

    @Query("DELETE FROM device_states")
    suspend fun clearAll()
}
