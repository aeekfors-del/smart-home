package com.besenok.smarthome.ui.screens.weather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    var showCityInput by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }

    if (showCityInput) {
        CityInputDialog(
            city = cityInput,
            onCityChange = { cityInput = it },
            lat = latInput,
            onLatChange = { latInput = it },
            lon = lonInput,
            onLonChange = { lonInput = it },
            onDismiss = { showCityInput = false },
            onSave = {
                val lat = latInput.toDoubleOrNull() ?: 55.7558
                val lon = lonInput.toDoubleOrNull() ?: 37.6173
                viewModel.loadWeather(lat, lon)
                showCityInput = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Погода") },
                actions = {
                    IconButton(onClick = { showCityInput = true }) {
                        Icon(Icons.Default.Edit, "Изменить город")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (weather) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is UiState.Success -> {
                    val data = (weather as UiState.Success).data
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "☀️",
                                    style = MaterialTheme.typography.displayLarge,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.width(24.dp))
                                Column {
                                    Text(data.city, style = MaterialTheme.typography.headlineLarge)
                                    Text(data.description, style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text("${data.temperature}°C", style = MaterialTheme.typography.displayLarge)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Ощущается", style = MaterialTheme.typography.bodySmall)
                                    Text("${data.feelsLike}°C", style = MaterialTheme.typography.bodyLarge)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Влажность", style = MaterialTheme.typography.bodySmall)
                                    Text("${data.humidity}%", style = MaterialTheme.typography.bodyLarge)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Давление", style = MaterialTheme.typography.bodySmall)
                                    Text("${data.pressure} мм", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    // Прогноз на 5 дней
                    val forecastData = (forecast as? UiState.Success)?.data ?: emptyList()
                    
                    if (forecastData.isNotEmpty()) {
                        Text("Прогноз на 5 дней", style = MaterialTheme.typography.titleLarge)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(forecastData) { entry ->
                                ForecastDayItem(
                                    day = entry.dayName,
                                    temp = String.format("%.0f°C", entry.temperature),
                                    condition = entry.condition
                                )
                            }
                        }
                    } else {
                        Text("Загрузка прогноза...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UiState.Error -> {
                    Text(
                        (weather as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun CityInputDialog(
    city: String,
    onCityChange: (String) -> Unit,
    lat: String,
    onLatChange: (String) -> Unit,
    lon: String,
    onLonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Город и координаты") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = onCityChange,
                    label = { Text("Город") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lat,
                    onValueChange = onLatChange,
                    label = { Text("Широта (lat)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = onLonChange,
                    label = { Text("Долгота (lon)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Пример: Москва 55.7558, 37.6173",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ForecastDayItem(day: String, temp: String, condition: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(day, style = MaterialTheme.typography.titleMedium)
            Text(condition, style = MaterialTheme.typography.bodyMedium)
            Text(temp, style = MaterialTheme.typography.titleMedium)
        }
    }
}
