package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

class SetDeviceStateUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke(deviceId: String, value: Any, ack: Boolean = true) =
        repository.setState(deviceId, value, ack)
}
