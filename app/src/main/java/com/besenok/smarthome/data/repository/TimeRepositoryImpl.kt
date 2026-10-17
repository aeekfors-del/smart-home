package com.besenok.smarthome.data.repository

import com.besenok.smarthome.data.remote.time.TimeApiService
import com.besenok.smarthome.domain.model.TimeInfo
import com.besenok.smarthome.domain.repository.TimeRepository
import com.besenok.smarthome.ui.common.UiState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeRepositoryImpl @Inject constructor(
    private val timeApiService: TimeApiService
) : TimeRepository {

    override suspend fun getCurrentTime(): UiState<TimeInfo> {
        return try {
            val response = timeApiService.getCurrentTime()
            val timeInfo = TimeInfo(
                dateTime = response.datetime,
                timezone = response.timezone,
                timestamp = response.timestamp,
                dayOfWeek = response.day_of_week.toString()
            )
            UiState.Success(timeInfo)
        } catch (e: Exception) {
            UiState.Error(e.message ?: "Failed to get time")
        }
    }
}
