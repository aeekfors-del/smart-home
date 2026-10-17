package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

class GetDevicesUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke() = repository.getDevices()
}
