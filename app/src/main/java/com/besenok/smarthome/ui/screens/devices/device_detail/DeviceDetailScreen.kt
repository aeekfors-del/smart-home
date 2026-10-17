package com.besenok.smarthome.ui.screens.devices.device_detail

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    val deviceState by viewModel.device.collectAsState()
    
    // Оптимистичное обновление - храним временные значения для каналов
    val temporaryStates = remember { mutableMapOf<String, Any>() }

    LaunchedEffect(deviceId) {
        viewModel.loadDevice(deviceId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали устройства") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = deviceState) {
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
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Ошибка: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            is UiState.Success -> {
                val device = state.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Информация об устройстве - компактная
                    item {
                        DeviceInfoCompact(device)
                    }

                    // Каналы устройства - компактные (фильтруем константные)
                    items(device.channels.filter { it.isInteractiveChannel() }, key = { it.id }) { channel ->
                        ChannelCompactCard(
                            channel = channel,
                            temporaryValue = temporaryStates[channel.id],
                            onToggle = { newValue ->
                                // Оптимистичное обновление - сразу обновляем UI
                                temporaryStates[channel.id] = newValue
                                viewModel.updateState(channel.id, newValue)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCompact(device: com.besenok.smarthome.domain.model.Device) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Тип: ${device.type} | Каналов: ${device.channels.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChannelCompactCard(
    channel: com.besenok.smarthome.domain.model.Device.Channel,
    temporaryValue: Any?,
    onToggle: (Any) -> Unit
) {
    // Используем temporary значение, если оно есть, иначе реальное
    val displayValue = temporaryValue ?: channel.state?.value
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть - название и значение
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                val unit = channel.state?.unit ?: ""
                val displayText = formatValueCompact(displayValue, unit)
                
                Text(
                    text = "Значение: $displayText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (channel.state?.ack == false && temporaryValue == null) {
                    Text(
                        text = "Не подтверждено",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Правая часть - элемент управления
            when (channel.controlType) {
                ControlType.SWITCH -> {
                    val isChecked = when (displayValue) {
                        is Boolean -> displayValue
                        is String -> displayValue.lowercase() !in listOf("false", "0", "off")
                        is Number -> displayValue.toInt() != 0
                        else -> false
                    }
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { newValue ->
                            // Всегда отправляем Boolean для SWITCH
                            onToggle(newValue)
                        }
                    )
                }
                ControlType.COLOR_PICKER -> {
                    // Парсим текущий цвет из строки "#RRGGBB"
                    val currentColor = remember(displayValue) {
                        parseColorFromString(displayValue)
                    }
                    var selectedColor by remember { mutableStateOf(currentColor) }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        // Кнопка выбора цвета
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .background(selectedColor, RoundedCornerShape(4.dp))
                                .clickable {
                                    // Отправляем текущий выбранный цвет
                                    val hexColor = colorToHex(selectedColor)
                                    onToggle(hexColor)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == Color.Transparent) {
                                Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        // Палитра предустановленных цветов
                        Row(
                            modifier = Modifier.height(30.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val presetColors = listOf(
                                Color.Red,
                                Color(0xFFFFA500),
                                Color.Yellow,
                                Color.Green,
                                Color.Blue,
                                Color.Magenta,
                                Color.Cyan,
                                Color.White
                            )
                            presetColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(color, CircleShape)
                                        .clickable {
                                            selectedColor = color
                                            val hexColor = colorToHex(color)
                                            onToggle(hexColor)
                                        }
                                )
                            }
                        }
                    }
                }
                ControlType.SLIDER -> {
                    // Преобразуем значение в диапазон 0-100
                    val sliderPercent = when (displayValue) {
                        is Int -> (displayValue.coerceIn(0, 100)).toFloat()
                        is Double -> (displayValue.coerceIn(0.0, 100.0)).toFloat()
                        is Float -> displayValue.coerceIn(0f, 100f)
                        is String -> displayValue.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
                        is Number -> (displayValue.toFloat().coerceIn(0f, 100f))
                        else -> 0f
                    }
                    
                    var tempValue by remember { mutableFloatStateOf(sliderPercent) }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Slider(
                            value = tempValue,
                            onValueChange = { newValue ->
                                tempValue = newValue
                                // Отправляем процент (0-100)
                                onToggle(tempValue.toInt())
                            },
                            onValueChangeFinished = {
                                // Гарантируем отправку при завершении
                                onToggle(tempValue.toInt())
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = "${tempValue.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ControlType.NONE -> {
                    // Только отображение, без элементов управления
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    // Для остальных типов - кнопка обновления
                    OutlinedButton(
                        onClick = { onToggle(displayValue ?: 0) },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Обновить",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun parseColorFromString(value: Any?): Color {
    return when (value) {
        is String -> {
            try {
                // Пробуем распарсить "#RRGGBB" или "#RRGGBBAA"
                val hex = value.trim('"')
                if (hex.startsWith("#")) {
                    val colorInt = hex.substring(1).toLong(16)
                    when (hex.length) {
                        7 -> Color(colorInt or 0xFF000000.toLong()) // Добавляем alpha
                        9 -> Color(colorInt)
                        else -> Color.White
                    }
                } else {
                    Color.White
                }
            } catch (e: Exception) {
                Color.White
            }
        }
        else -> Color.White
    }
}

private fun colorToHex(color: Color): String {
    return String.format("#%02X%02X%02X", (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
}

private fun formatValueCompact(value: Any?, unit: String?): String {
    if (value == null) return "Недоступно"
    
    val text = when (value) {
        is Boolean -> if (value) "Вкл" else "Выкл"
        is String -> value
        is Number -> value.toString()
        else -> value.toString()
    }
    
    return if (!unit.isNullOrEmpty()) "$text $unit" else text
}

/**
 * Проверяет, является ли канал интерактивным (поддерживает управление)
 * Константные каналы (IP, version, firmwareVersion и т.д.) возвращают false
 */
private fun com.besenok.smarthome.domain.model.Device.Channel.isInteractiveChannel(): Boolean {
    val roleName = name.lowercase()
    val channelId = id.lowercase()
    
    // Список константных атрибутов, которые не должны быть интерактивными
    val constantPatterns = listOf(
        "ip", "address",
        "version", "firmwareversion", "firmware_version",
        "manufacturer", "model", "type",
        "connected", "online",
        "battery",
        "id", "oid",
        "timestamp", "time"
    )
    
    return constantPatterns.none { pattern ->
        roleName.contains(pattern, ignoreCase = true) || 
        channelId.contains(pattern, ignoreCase = true)
    }
}
