package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

/**
 * Значение одного параметра (состояния) ioBroker
 */
class GetParameterValueUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke(objectId: String) = repository.getState(objectId)
}
