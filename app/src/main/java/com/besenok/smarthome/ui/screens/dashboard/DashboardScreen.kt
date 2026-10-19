package com.besenok.smarthome.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.model.WeatherInfo
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val weather by viewModel.weatherState.collectAsState()
    val forecast by viewModel.forecastState.collectAsState()
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
                // TODO: Добавить метод loadWeatherWithForecast в DashboardViewModel
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
                item(
                    key = "forecast",
                    contentType = "forecast",
                    span = { GridItemSpan(3) }
                ) {
                    // Cell 7-9: Прогноз на 5 дней (на всю ширину)
                    ForecastWidgetCard(forecast)
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
fun ForecastWidgetCard(forecast: UiState<List<WeatherInfo.ForecastEntry>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Прогноз на 5 дней",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        when (forecast) {
            is UiState.Loading -> {
                CircularProgressIndicator()
            }
            is UiState.Success -> {
                val forecastList = forecast.data
                if (forecastList.isEmpty()) {
                    Text(
                        "Нет данных о прогнозе",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Отображаем прогноз с фиксированным размером карточек
                    ForecastRow(forecastList)
                }
            }
            is UiState.Error -> {
                Text(
                    forecast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ForecastRow(forecastList: List<WeatherInfo.ForecastEntry>) {
    // Показываем первые 5 дней
    val displayList = forecastList.take(5)
    
    // Карточки фиксированного размера с равными отступами
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        displayList.forEach { entry ->
            ForecastDayCardCompact(entry)
        }
    }
}

@Composable
fun ForecastDayCardCompact(entry: WeatherInfo.ForecastEntry) {
    val weatherIcon = getWeatherIcon(entry.iconCode)
    
    // Карточка с полной информацией о погоде
    Card(
        modifier = Modifier
            .width(160.dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Строка 1: День недели + иконка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.dayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(weatherIcon, style = MaterialTheme.typography.headlineSmall)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Строка 2: Температура (основная)
            Text(
                String.format("%.0f°", entry.temperature),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Строка 3: Min/Max температура
            Text(
                String.format("↓%.0f° ↑%.0f°", entry.tempMin, entry.tempMax),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Строка 4: Ощущается как
            Text(
                String.format("Ощущается: %.0f°", entry.feelsLike),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Разделитель
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            
            // Строка 5: Влажность + Давление
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Text("💧", style = MaterialTheme.typography.bodySmall)
                    Text(
                        " ${entry.humidity}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row {
                    Text("🌡️", style = MaterialTheme.typography.bodySmall)
                    Text(
                        " ${entry.pressure} мм",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Строка 6: Ветер
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "💨 ${String.format("%.1f}", entry.windSpeed)} м/с",
                    style = MaterialTheme.typography.bodySmall
                )
                if (entry.windGust > 0.5) {
                    Text(
                        "🌬️ ${String.format("%.1f}", entry.windGust)} м/с",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Строка 7: Облачность + Вероятность осадков
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    String.format("☁️ %d%%", entry.cloudiness),
                    style = MaterialTheme.typography.bodySmall
                )
                if (entry.precipitationProbability > 0.05) {
                    Text(
                        String.format("🌧️ %.0f%%", entry.precipitationProbability * 100),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Видимость (если > 0)
            if (entry.visibility > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    String.format("👁️ %.1f км", entry.visibility / 1000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

