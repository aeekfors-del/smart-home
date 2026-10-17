package com.besenok.smarthome.ui.screens.sensors

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(viewModel: SensorsViewModel = hiltViewModel()) {
    val sensors by viewModel.sensors.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Мониторинг") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (sensors) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is UiState.Success -> {
                    val sensorList = (sensors as UiState.Success).data
                    if (sensorList.isEmpty()) {
                        Text(
                            "Нет датчиков",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        sensorList.forEach { sensor ->
                            SensorCard(
                                name = sensor.name,
                                value = sensor.value?.toString() ?: "—",
                                unit = sensor.unit,
                                icon = when (sensor.sensorType) {
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.TEMPERATURE -> "🌡️"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.HUMIDITY -> "💧"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.PRESSURE -> "📊"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.LIGHT -> "💡"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.MOTION -> "🚶"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.DOOR_WINDOW -> "🚪"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.GAS -> "💨"
                                    com.besenok.smarthome.domain.model.Sensor.SensorType.SMOKE -> "🔥"
                                }
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Text(
                        (sensors as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun SensorCard(name: String, value: String, unit: String, icon: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text("Обновлено: только что", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
