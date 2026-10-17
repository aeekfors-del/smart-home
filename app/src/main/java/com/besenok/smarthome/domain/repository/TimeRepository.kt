package com.besenok.smarthome.domain.repository

import com.besenok.smarthome.domain.model.TimeInfo
import com.besenok.smarthome.ui.common.UiState

interface TimeRepository {
    suspend fun getCurrentTime(): UiState<TimeInfo>
}
