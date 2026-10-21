package com.besenok.smarthome.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val username by viewModel.username.collectAsState()
    val hasPassword by viewModel.hasPassword.collectAsState()
    val weatherRegionName by viewModel.weatherRegionName.collectAsState()
    val weatherRegion by viewModel.weatherRegion.collectAsState()
    val weatherRegions = viewModel.weatherRegions
    val connectionUiState by viewModel.connectionState.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val themeOptions = viewModel.themeOptions
    val themeOptionsDisplay = viewModel.themeOptionsDisplay
    val openWeatherMapApiKey by viewModel.openWeatherMapApiKey.collectAsState()

    var showRegionDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    // Local editable state - initialized once
    var localHost by remember { mutableStateOf(host) }
    var localPort by remember { mutableStateOf(port) }
    var localUsername by remember { mutableStateOf(username) }
    // Пустое значение = не менять сохранённый пароль
    var localPassword by remember { mutableStateOf("") }
    var localOpenWeatherMapApiKey by remember { mutableStateOf(openWeatherMapApiKey) }

    // Update local state only when ViewModel changes (e.g., after save)
    DisposableEffect(host) { localHost = host; onDispose {} }
    DisposableEffect(port) { localPort = port; onDispose {} }
    DisposableEffect(username) { localUsername = username; onDispose {} }
    DisposableEffect(openWeatherMapApiKey) { localOpenWeatherMapApiKey = openWeatherMapApiKey; onDispose {} }

    if (showRegionDialog) {
        RegionSelectionDialog(
            regions = weatherRegions,
            selectedRegion = weatherRegion,
            onRegionSelected = { regionOrCoords ->
                viewModel.saveWeatherRegion(regionOrCoords)
                showRegionDialog = false
            },
            onDismiss = { showRegionDialog = false }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить настройки") },
            text = { Text("Сохранить изменения настроек ioBroker и API ключ OpenWeatherMap?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveIoBrokerSettings(localHost, localPort, localUsername, localPassword)
                    localPassword = ""
                    viewModel.saveOpenWeatherMapApiKey(localOpenWeatherMapApiKey)
                    showSaveDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSaveDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Сохранить",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== ГРУППА 1: НАСТРОЙКИ ioBroker =====
            item {
                Text(
                    "🔌 Настройки подключения",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = localHost,
                            onValueChange = { localHost = it },
                            label = { Text("Host (IP ioBroker)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = { Text("Адрес вашего ioBroker сервера") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localPort,
                            onValueChange = { localPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { Text("Порт ioBroker (обычно 8081)") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localUsername,
                            onValueChange = { localUsername = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("Логин для доступа к ioBroker") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localPassword,
                            onValueChange = { localPassword = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            supportingText = {
                                Text(
                                    if (hasPassword) "Пароль сохранён. Оставьте пустым, чтобы не менять"
                                    else "Пароль для доступа к ioBroker"
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                isTesting = true
                                viewModel.testConnection()
                            },
                            enabled = !isTesting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Проверка...")
                            } else {
                                Text("📡 Проверить подключение")
                            }
                        }

                        // Display connection test result
                        val connectionState = connectionUiState
                        when (connectionState) {
                            is UiState.Loading -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Проверка подключения...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is UiState.Success -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = connectionState.data,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            is UiState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = connectionState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== ГРУППА 2: ОФОРМЛЕНИЕ =====
            item {
                Text(
                    "🎨 Оформление",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Тема приложения",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        themeOptions.forEach { theme ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.saveAppTheme(theme)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = appTheme == theme,
                                    onClick = { viewModel.saveAppTheme(theme) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        themeOptionsDisplay[theme] ?: theme,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (appTheme == theme)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    if (appTheme == theme) {
                                        Text(
                                            "Выбрано",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== ГРУППА 3: ПОГОДА =====
            item {
                Text(
                    "🌤️ Погода",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Регион для отображения погоды:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = { showRegionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(weatherRegionName)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Display current coordinates
                        val coords = weatherRegion.split(",")
                        if (coords.size == 2) {
                            val lat = coords[0]
                            val lon = coords[1]
                            Text(
                                text = "Текущие координаты: $lat°, $lon°",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localOpenWeatherMapApiKey,
                            onValueChange = { localOpenWeatherMapApiKey = it },
                            label = { Text("API ключ OpenWeatherMap") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Вставьте ваш API ключ") },
                            supportingText = {
                                Text("Получить бесплатно: https://openweathermap.org/api (1000 запросов/день)")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Координаты: ${weatherRegionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== ГРУППА 4: ИСТОЧНИКИ ДАННЫХ =====
            item {
                Text(
                    "☁️ Источники данных",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("🔌", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ioBroker", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("🌤️", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OpenWeatherMap", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ===== ГРУППА 5: О ПРИЛОЖЕНИИ =====
            item {
                Text(
                    "ℹ️ О приложении",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SmartHome v1.0", style = MaterialTheme.typography.bodyMedium)
                        Text("Управление умным домом через ioBroker", style = MaterialTheme.typography.bodySmall)
                        Text("© 2026 Besenok", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun RegionSelectionDialog(
    regions: List<String>,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var customCityName by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    val regionsMap = AppPreferencesDataSource.WEATHER_REGIONS

    val filteredRegions = regions.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    val tabs = listOf("🔍 Поиск города", "📍 GPS координаты")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите регион") },
        text = {
            Column {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Search cities
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Поиск города...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredRegions.isEmpty() && searchQuery.isNotBlank()) {
                            Text(
                                "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(filteredRegions, key = { region -> region }) { region ->
                                val coords = regionsMap[region]
                                val isSelected = coords == selectedRegion

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRegionSelected(region) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (isSelected) "✅" else "⭕",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            region,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        coords?.let { c ->
                                            Text(
                                                c.replace(",", "°"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // GPS coordinates input
                        Column {
                            OutlinedTextField(
                                value = customCityName,
                                onValueChange = { customCityName = it },
                                label = { Text("Название города (необязательно)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Например: Мой город") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = latitude,
                                    onValueChange = { lat ->
                                        if (lat.isEmpty() || lat.toDoubleOrNull() != null) {
                                            latitude = lat
                                        }
                                    },
                                    label = { Text("Широта (lat)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    placeholder = { Text("55.7558") },
                                    supportingText = { Text("от -90 до 90") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, contentDescription = null)
                                    }
                                )

                                OutlinedTextField(
                                    value = longitude,
                                    onValueChange = { lon ->
                                        if (lon.isEmpty() || lon.toDoubleOrNull() != null) {
                                            longitude = lon
                                        }
                                    },
                                    label = { Text("Долгота (lon)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    placeholder = { Text("37.6173") },
                                    supportingText = { Text("от -180 до 180") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, contentDescription = null)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Validation message
                            val latValid = latitude.toDoubleOrNull()
                            val lonValid = longitude.toDoubleOrNull()
                            
                            if (latValid != null && lonValid != null) {
                                val isValidLat = latValid in -90.0..90.0
                                val isValidLon = lonValid in -180.0..180.0
                                
                                if (isValidLat && isValidLon) {
                                    val cityName = customCityName.ifBlank { "${latitude}°, ${longitude}°" }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "✅ Координаты валидны",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "Город: $cityName",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "❌ Неверный диапазон координат",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else if (latitude.isNotBlank() || longitude.isNotBlank()) {
                                Text(
                                    "Введите оба значения",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                
                Button(
                    onClick = {
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            val cityName = customCityName.ifBlank { "GPS: $lat, $lon" }
                            onRegionSelected("${lat},$lon")
                        }
                    },
                    enabled = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                ) {
                    Text("Сохранить")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    )
}
