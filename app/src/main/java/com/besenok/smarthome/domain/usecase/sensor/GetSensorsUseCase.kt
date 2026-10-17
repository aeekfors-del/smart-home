package com.besenok.smarthome.domain.usecase.sensor

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

class GetSensorsUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke() = repository.getSensors()
}
