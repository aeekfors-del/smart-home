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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
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

    // Параметр, который сейчас переименовывается
    var renamingChannel by remember { mutableStateOf<Device.Channel?>(null) }

    LaunchedEffect(deviceId) {
        viewModel.loadDevice(deviceId)
    }

    // Диалог переименования параметра
    renamingChannel?.let { channel ->
        RenameParameterDialog(
            parameterName = channel.name,
            onDismiss = { renamingChannel = null },
            onConfirm = { newName ->
                viewModel.renameParameter(channel.id, newName)
                renamingChannel = null
            }
        )
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
                            },
                            onRename = { renamingChannel = channel }
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
    onToggle: (Any) -> Unit,
    onRename: () -> Unit
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
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть - название и значение
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Переименовать параметр",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                
                val unit = channel.state?.unit ?: ""
                val displayText = formatValueCompact(displayValue, unit)
                val isBoolean = isBooleanValue(displayValue)
                
                Text(
                    text = displayText,
                    style = if (isBoolean) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Правая часть - элемент управления
            when (channel.controlType) {
                ControlType.SWITCH -> {
                    if (isBooleanValue(displayValue)) {
                        val isChecked = when (displayValue) {
                            is Boolean -> displayValue
                            is String -> displayValue.lowercase() !in listOf("false", "0", "off")
                            is Number -> displayValue.toInt() != 0
                            else -> false
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { newValue ->
                                onToggle(newValue)
                            }
                        )
                    }
                }
                ControlType.COLOR_PICKER -> {
                    // Парсим текущий цвет из строки "#RRGGBB"
                    val currentColor = remember(displayValue) {
                        parseColorFromString(displayValue)
                    }
                    var selectedColor by remember { mutableStateOf(currentColor) }
                    
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(60.dp)) {
                        // Кнопка выбора цвета (компактная)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                .background(selectedColor, RoundedCornerShape(3.dp))
                                .clickable {
                                    val hexColor = colorToHex(selectedColor)
                                    onToggle(hexColor)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == Color.Transparent) {
                                Text("+", fontSize = MaterialTheme.typography.bodySmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        // Палитра предустановленных цветов (компактная)
                        Row(
                            modifier = Modifier.height(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
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
                                        .size(14.dp)
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
                    
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
                        Slider(
                            value = tempValue,
                            onValueChange = { newValue ->
                                tempValue = newValue
                                onToggle(tempValue.toInt())
                            },
                            onValueChangeFinished = {
                                onToggle(tempValue.toInt())
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.width(70.dp)
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

private fun isBooleanValue(value: Any?): Boolean {
    return when (value) {
        is Boolean -> true
        is String -> value.lowercase() in listOf("true", "false", "on", "off", "1", "0", "yes", "no")
        is Number -> value.toInt() in listOf(0, 1)
        else -> false
    }
}

private fun formatValueCompact(value: Any?, unit: String?): String {
    if (value == null) return "Недоступно"
    
    val text = when (value) {
        is Boolean -> if (value) "Вкл" else "Выкл"
        is String -> when (value.lowercase()) {
            "true", "on", "1", "yes" -> "Вкл"
            "false", "off", "0", "no" -> "Выкл"
            else -> value
        }
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
