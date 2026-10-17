package com.besenok.smarthome.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.Scenario
import com.besenok.smarthome.domain.usecase.scenario.ExecuteScenarioUseCase
import com.besenok.smarthome.domain.usecase.scenario.GetScenariosUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScenarioListViewModel @Inject constructor(
    private val getScenariosUseCase: GetScenariosUseCase,
    private val executeScenarioUseCase: ExecuteScenarioUseCase
) : ViewModel() {

    private val _scenarios = MutableStateFlow<UiState<List<Scenario>>>(UiState.Loading)
    val scenarios: StateFlow<UiState<List<Scenario>>> = _scenarios.asStateFlow()

    init {
        loadScenarios()
    }

    fun loadScenarios() {
        viewModelScope.launch {
            try {
                _scenarios.value = getScenariosUseCase()
            } catch (e: Exception) {
                _scenarios.value = UiState.Error(e.message ?: "Failed to load scenarios")
            }
        }
    }

    fun toggleScenario(scenario: Scenario) {
        viewModelScope.launch {
            try {
                executeScenarioUseCase(scenario.id)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
