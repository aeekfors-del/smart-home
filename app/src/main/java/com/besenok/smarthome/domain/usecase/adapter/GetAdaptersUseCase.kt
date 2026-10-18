package com.besenok.smarthome.domain.usecase.adapter

import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.ui.common.UiState
import javax.inject.Inject

/**
 * UseCase для получения списка адаптеров ioBroker
 */
class GetAdaptersUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke(): UiState<List<AdapterInfo>> {
        return repository.getAdapters()
    }
}
