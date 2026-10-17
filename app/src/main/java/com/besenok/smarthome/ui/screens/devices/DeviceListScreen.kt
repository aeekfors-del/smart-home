package com.besenok.smarthome.ui.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Devices
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.ui.common.UiState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onDeviceClick: (String) -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val devicesState by viewModel.devices.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val editingDevice by viewModel.editingDevice.collectAsState()

    val filters = listOf(
        DeviceListViewModel.DeviceFilter.ALL to "Все",
        DeviceListViewModel.DeviceFilter.LIGHT to "Свет",
        DeviceListViewModel.DeviceFilter.SOCKET to "Розетки",
        DeviceListViewModel.DeviceFilter.SENSOR to "Датчики"
    )

    // Диалог переименования
    editingDevice?.let { device ->
        RenameDeviceDialog(
            device = device,
            onDismiss = viewModel::cancelRenameDevice,
            onConfirm = viewModel::confirmRenameDevice
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Устройства") })
        },
        bottomBar = {
            TabRow(selectedTabIndex = selectedFilter.ordinal) {
                filters.forEachIndexed { index, (filter, label) ->
                    Tab(
                        selected = index == selectedFilter.ordinal,
                        onClick = { viewModel.setFilter(filter) },
                        text = { Text(label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (devicesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Success -> {
                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет устройств",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Добавляем заголовки групп и устройства
                        groups.forEach { group ->
                            // Заголовок группы
                            item {
                                DeviceGroupHeader(
                                    group = group,
                                    onGroupClick = { viewModel.toggleGroup(group.id) }
                                )
                            }
                            // Устройства в группе (если развернута)
                            if (group.isExpanded) {
                                // Первая строка
                                if (group.devices.isNotEmpty()) {
                                    item {
                                        DeviceRow(
                                            devices = group.devices.take(2),
                                            onDeviceClick = onDeviceClick,
                                            onDeviceEdit = viewModel::startRenameDevice
                                        )
                                    }
                                }
                                // Вторая строка
                                if (group.devices.size > 2) {
                                    item {
                                        DeviceRow(
                                            devices = group.devices.drop(2).take(2),
                                            onDeviceClick = onDeviceClick,
                                            onDeviceEdit = viewModel::startRenameDevice
                                        )
                                    }
                                }
                                // Третья строка
                                if (group.devices.size > 4) {
                                    item {
                                        DeviceRow(
                                            devices = group.devices.drop(4).take(2),
                                            onDeviceClick = onDeviceClick,
                                            onDeviceEdit = viewModel::startRenameDevice
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (devicesState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceGroupHeader(
    group: DeviceGroup,
    onGroupClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onGroupClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${group.deviceCount} устр.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = if (group.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (group.isExpanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeviceRow(
    devices: List<Device>,
    onDeviceClick: (String) -> Unit,
    onDeviceEdit: (Device) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        devices.forEach { device ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                DeviceGridCard(
                    device = device,
                    onClick = { onDeviceClick(device.id) },
                    onEdit = { onDeviceEdit(device) }
                )
            }
        }
        // Заполняем пустые места если устройств нечётное количество
        for (i in devices.size until 2) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}



@Composable
fun DeviceGridCard(
    device: Device,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val (icon, statusText) = getDeviceStatusText(device)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Заголовок с иконкой
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            device.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                
                // Статусы - компактно
                if (statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            
            // Кнопка редактирования (переименования)
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
                onClick = onEdit
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Переименовать",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class DeviceStatus(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val statusText: String
)

@Composable
private fun getDeviceStatusText(device: Device): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    val statusParts = mutableListOf<String>()
    
    // Определяем иконку устройства
    val icon = when {
        device.name.contains("Температура") || device.name.contains("sensor_ht") -> Icons.Default.Thermostat
        device.name.contains("Датчик движения") || device.name.contains("motion") -> Icons.Default.MotionPhotosOn
        device.name.contains("Датчик открытия") || device.name.contains("magnet") -> Icons.Default.DoorFront
        device.name.contains("Gateway") -> Icons.Default.Router
        device.name.contains("Погода") || device.name.contains("weather") -> Icons.Default.WbSunny
        device.name.contains("Кнопка") || device.name.contains("switch") -> Icons.Default.SquareFoot
        device.name.contains("Освещенность") || device.name.contains("illumination") -> Icons.Default.Lightbulb
        else -> Icons.Default.Devices
    }
    
    // Температура
    val tempChannel = device.channels.find { it.name.contains("temperature", ignoreCase = true) }
    if (tempChannel != null && tempChannel.state?.value != null) {
        val tempValue = parseDoubleValue(tempChannel.state!!.value)
        statusParts.add(String.format("%.1f°C", tempValue))
    }
    
    // Влажность
    val humidityChannel = device.channels.find { it.name.contains("humidity", ignoreCase = true) }
    if (humidityChannel != null && humidityChannel.state?.value != null) {
        val humidityValue = parseIntValue(humidityChannel.state!!.value)
        statusParts.add(String.format("%d%%", humidityValue))
    }
    
    // Освещенность
    val illumChannel = device.channels.find { it.name.contains("illumination", ignoreCase = true) ||
        it.name.contains("lux", ignoreCase = true) }
    if (illumChannel != null && illumChannel.state?.value != null) {
        val illumValue = parseIntValue(illumChannel.state!!.value)
        statusParts.add("$illumValue lux")
    }
    
    // Давление
    val pressureChannel = device.channels.find { it.name.contains("pressure", ignoreCase = true) }
    if (pressureChannel != null && pressureChannel.state?.value != null) {
        val pressureValue = parseIntValue(pressureChannel.state!!.value)
        statusParts.add("$pressureValue mmHg")
    }
    
    // Датчик движения
    val motionChannel = device.channels.find { it.name.contains("motion", ignoreCase = true) ||
        it.name.contains("is motion", ignoreCase = true) }
    if (motionChannel != null && motionChannel.state?.value != null) {
        val isMotion = parseBooleanValue(motionChannel.state!!.value)
        if (isMotion) {
            statusParts.add("Движение!")
        }
    }
    
    // Датчик открытия
    val magnetChannel = device.channels.find { it.name.contains("opened", ignoreCase = true) ||
        (it.name.contains("state", ignoreCase = true) && !it.name.contains("motion")) }
    if (magnetChannel != null && magnetChannel.state?.value != null && tempChannel == null && humidityChannel == null) {
        val isOpen = parseBooleanValue(magnetChannel.state!!.value)
        if (isOpen) {
            statusParts.add("Открыто")
        } else {
            statusParts.add("Закрыто")
        }
    }
    
    // Battery percent - ищем именно percent
    val batteryChannel = device.channels.find { it.name.contains("percent", ignoreCase = true) }
    if (batteryChannel != null && batteryChannel.state?.value != null) {
        val batteryValue = parseIntValue(batteryChannel.state!!.value)
        val batteryIcon = when {
            batteryValue > 50 -> Icons.Filled.BatteryFull
            batteryValue > 20 -> Icons.Filled.BatteryAlert
            else -> Icons.Filled.BatteryAlert
        }
        // Добавляем батарею в начало списка
        statusParts.add(0, String.format("🔋%d%%", batteryValue))
    }
    
    return icon to statusParts.joinToString("  •  ")
}

private fun parseDoubleValue(value: Any?): Double {
    return when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        is Number -> value.toDouble()
        else -> 0.0
    }
}

private fun parseIntValue(value: Any?): Int {
    return when (value) {
        is Int -> value
        is Double -> value.toInt()
        is Float -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        is Number -> value.toInt()
        else -> 0
    }
}

private fun parseBooleanValue(value: Any?): Boolean {
    return when (value) {
        is Boolean -> value
        is String -> value.toBoolean()
        is Number -> value.toInt() == 1
        else -> false
    }
}
