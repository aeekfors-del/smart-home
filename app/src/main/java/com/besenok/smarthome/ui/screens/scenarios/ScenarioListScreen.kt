package com.besenok.smarthome.ui.screens.scenarios

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioListScreen(viewModel: ScenarioListViewModel = hiltViewModel()) {
    val scenarios by viewModel.scenarios.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Сценарии") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: Show create dialog */ }) {
                Icon(Icons.Default.Add, "Создать")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (scenarios) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is UiState.Success -> {
                    val scenarioList = (scenarios as UiState.Success).data
                    if (scenarioList.isEmpty()) {
                        Text(
                            "Нет сценариев",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        scenarioList.forEach { scenario ->
                            ScenarioListItem(
                                name = scenario.name,
                                description = scenario.description,
                                enabled = scenario.enabled,
                                onToggle = { viewModel.toggleScenario(scenario) }
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Text(
                        (scenarios as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun ScenarioListItem(name: String, description: String?, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
