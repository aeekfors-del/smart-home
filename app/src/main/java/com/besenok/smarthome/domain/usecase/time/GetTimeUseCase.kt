package com.besenok.smarthome.domain.usecase.time

import com.besenok.smarthome.domain.repository.TimeRepository
import javax.inject.Inject

class GetTimeUseCase @Inject constructor(
    private val repository: TimeRepository
) {
    suspend operator fun invoke() = repository.getCurrentTime()
}
