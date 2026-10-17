package com.besenok.smarthome.data.local.room.dao

import androidx.room.*
import com.besenok.smarthome.data.local.room.entity.ScenarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {

    @Query("SELECT * FROM scenarios")
    fun getAllScenarios(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getScenarioById(id: String): ScenarioEntity?

    @Upsert
    suspend fun upsertScenario(scenario: ScenarioEntity)

    @Delete
    suspend fun deleteScenario(scenario: ScenarioEntity)

    @Query("DELETE FROM scenarios")
    suspend fun clearAll()
}
