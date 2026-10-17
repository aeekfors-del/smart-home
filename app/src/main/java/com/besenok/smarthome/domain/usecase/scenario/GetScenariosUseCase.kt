package com.besenok.smarthome.domain.usecase.scenario

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

class GetScenariosUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke() = repository.getScenarios()
}
