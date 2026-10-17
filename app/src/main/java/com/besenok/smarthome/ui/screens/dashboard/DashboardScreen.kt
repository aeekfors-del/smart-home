package com.besenok.smarthome.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.ui.common.UiState
import com.besenok.smarthome.ui.screens.weather.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    weatherViewModel: WeatherViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val weather by weatherViewModel.weather.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { AppPreferencesDataSource(context) }
    val weatherRegion by preferences.weatherRegion.collectAsState(
        initial = AppPreferencesDataSource.DEFAULT_WEATHER_REGION
    )
    
    // Загружаем погоду при изменении региона
    LaunchedEffect(weatherRegion) {
        val coords = weatherRegion.split(",")
        if (coords.size == 2) {
            val lat = coords[0].toDoubleOrNull()
            val lon = coords[1].toDoubleOrNull()
            if (lat != null && lon != null) {
                weatherViewModel.loadWeather(lat, lon)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Умный дом") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Grid 3x3
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ===== ROW 1 =====
                item {
                    // Cell 1: Время
                    TimeWidgetCard()
                }
                item {
                    // Cell 2: Погода
                    WeatherWidgetCard(weather)
                }
                item {
                    // Cell 3: Статус брокера
                    ConnectionStatusCard(connectionState)
                }
                
                // ===== ROW 2 =====
                item {
                    // Cell 4: Пусто
                    DashboardPlaceholderCard()
                }
                item {
                    // Cell 5: Пусто
                    DashboardPlaceholderCard()
                }
                item {
                    // Cell 6: Пусто
                    DashboardPlaceholderCard()
                }
                
                // ===== ROW 3 =====
                item {
                    // Cell 7: Пусто
                    DashboardPlaceholderCard()
                }
                item {
                    // Cell 8: Пусто
                    DashboardPlaceholderCard()
                }
                item {
                    // Cell 9: Пусто
                    DashboardPlaceholderCard()
                }
            }
        }
    }
}

@Composable
fun DashboardPlaceholderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(state: com.besenok.smarthome.domain.repository.ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is com.besenok.smarthome.domain.repository.ConnectionState.Connected ->
                    MaterialTheme.colorScheme.tertiaryContainer
                is com.besenok.smarthome.domain.repository.ConnectionState.Error ->
                    MaterialTheme.colorScheme.errorContainer
                else ->
                    MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ioBroker logo
            Text(
                "🔷",
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                "ioBroker",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when (state) {
                    is com.besenok.smarthome.domain.repository.ConnectionState.Connected -> "Подключено"
                    is com.besenok.smarthome.domain.repository.ConnectionState.Error -> "Ошибка"
                    else -> "Подключение..."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun WeatherWidgetCard(weather: UiState<com.besenok.smarthome.domain.model.WeatherInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            when (weather) {
                is UiState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏳", style = MaterialTheme.typography.headlineMedium)
                        Text("Загрузка...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is UiState.Success -> {
                    val data = weather.data
                    val weatherIcon = getWeatherIcon(data.iconCode)
                    val windDir = getWindDirection(data.windDeg)
                    
                    // Верхняя строка: иконка, температура, город
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(weatherIcon, style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text("${data.temperature}°C", style = MaterialTheme.typography.headlineLarge)
                            Text(data.condition, style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(data.city, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Нижняя строка: влажность, ветер
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            Text("💧", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${data.humidity}%", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Row {
                            Text("💨", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${String.format("%.1f", data.windSpeed)} м/с $windDir",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("❌", style = MaterialTheme.typography.headlineMedium)
                        Text(weather.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun getWindDirection(degrees: Int): String {
    return when (degrees) {
        in 0..22 -> "С"
        in 23..67 -> "СВ"
        in 68..112 -> "В"
        in 113..157 -> "ЮВ"
        in 158..202 -> "Ю"
        in 203..247 -> "ЮЗ"
        in 248..292 -> "З"
        in 293..337 -> "СЗ"
        in 338..360 -> "С"
        else -> ""
    }
}

private fun getWeatherIcon(iconCode: String): String {
    // OpenWeatherMap icon codes (e.g., 01d, 02n, 10d, etc.)
    if (iconCode.length >= 3) {
        val mainIcon = iconCode.substring(0, 2)
        val isNight = iconCode.endsWith("n")
        
        return when (mainIcon) {
            "01" -> if (isNight) "🌙" else "☀️"
            "02" -> if (isNight) "🌙" else "⛅"
            "03", "04" -> "☁️"
            "09" -> "🌧️"
            "10" -> "🌦️"
            "11" -> "⛈️"
            "13" -> "❄️"
            "50" -> "🌫️"
            else -> if (isNight) "🌙" else "🌡️"
        }
    }
    
    // Fallback for condition names
    return when (iconCode.lowercase()) {
        "clear", "day" -> "☀️"
        "partly-cloudy", "partly_cloudy", "day-cloudy" -> "⛅"
        "cloudy", "overcast", "night-cloudy", "night-overcast" -> "☁️"
        "rain", "light-rain", "heavy-rain", "moderate-rain", "wet-rain" -> "🌧️"
        "drizzle", "light-drizzle", "heavy-drizzle" -> "🌦️"
        "snow", "light-snow", "heavy-snow", "snow-showers" -> "❄️"
        "thunderstorm" -> "⛈️"
        "fog", "mist", "haze" -> "🌫️"
        "sleet", "snow-showers-with-thunderstorm" -> "🌨️"
        "hail" -> "🧊"
        else -> "🌡️"
    }
}

@Composable
fun TimeWidgetCard() {
    var currentTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    
    // Обновляем каждую секунду
    DisposableEffect(Unit) {
        val handler = android.os.Handler()
        val runnable = object : Runnable {
            override fun run() {
                currentTime = java.time.LocalDateTime.now()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacks(runnable) }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val timeStr = String.format("%02d:%02d:%02d", currentTime.hour, currentTime.minute, currentTime.second)
            Text(timeStr, style = MaterialTheme.typography.headlineLarge)
            
            val days = listOf("", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val months = listOf("", "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
            val dateStr = "${days[currentTime.dayOfWeek.value]}, ${currentTime.dayOfMonth} ${months[currentTime.monthValue - 1]}"
            
            Text(dateStr, style = MaterialTheme.typography.headlineLarge)
        }
    }
}
