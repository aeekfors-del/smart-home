package com.besenok.smarthome.domain.usecase.scenario

import com.besenok.smarthome.domain.repository.IobrokerRepository
import javax.inject.Inject

class ExecuteScenarioUseCase @Inject constructor(
    private val repository: IobrokerRepository
) {
    suspend operator fun invoke(scenarioId: String) = repository.executeScenario(scenarioId)
}
