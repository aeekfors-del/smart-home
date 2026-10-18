package com.besenok.smarthome.domain.usecase.adapter

import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.ui.common.UiState
import javax.inject.Inject

/**
 * UseCase для получения устройств из адаптера ioBroker
 */
class GetDevicesFromAdapterUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke(adapterId: String): UiState<List<com.besenok.smarthome.domain.model.Device>> {
        return repository.getDevicesFromAdapter(adapterId)
    }
}
