package com.besenok.smarthome.ui.screens.devices

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.ui.common.UiState
import kotlinx.serialization.json.JsonObject

private const val TAG = "AddDeviceDialog"

/**
 * Диалог выбора адаптера для добавления устройств
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    adaptersState: UiState<List<JsonObject>>,
    devicesState: UiState<List<Device>>,
    onAddDevices: (List<Device>) -> Unit,
    onSelectedDevicesChanged: (List<Device>) -> Unit,
    selectedAdapterId: String? = null,
    onAdapterSelected: (String) -> Unit = {}
) {
    var selectedDevices by remember { mutableStateOf(mutableSetOf<Device>()) }
    var showingDeviceList by remember { mutableStateOf(false) }
    var currentAdapterId by remember { mutableStateOf<String?>(null) }
    
    // Собираем устройства в список для отображения
    val devicesList = when (val state = devicesState) {
        is UiState.Success -> {
            Log.d(TAG, "DevicesState is Success, data size: ${state.data?.size}")
            state.data
        }
        is UiState.Loading -> {
            Log.d(TAG, "DevicesState is Loading")
            null
        }
        is UiState.Error -> {
            Log.d(TAG, "DevicesState is Error: ${state.message}")
            null
        }
    } ?: emptyList()
    
    // Автоматически показываем список устройств когда они загружены
    LaunchedEffect(devicesState, currentAdapterId) {
        if (devicesState is UiState.Success && currentAdapterId != null) {
            showingDeviceList = true
        }
    }
    
    val toggleDevice: (Device) -> Unit = { device ->
        val newSet = selectedDevices.toMutableSet()
        if (newSet.contains(device)) {
            newSet.remove(device)
        } else {
            newSet.add(device)
        }
        selectedDevices = newSet
        onSelectedDevicesChanged(selectedDevices.toList())
    }
    
    AlertDialog(
        onDismissRequest = {
            if (showingDeviceList) {
                showingDeviceList = false
            } else {
                onDismiss()
            }
        },
        title = { 
            Text(if (showingDeviceList) "Выберите устройства" else "Добавить устройство") 
        },
        text = {
            if (showingDeviceList) {
                // Показываем список устройств для выбора
                DeviceSelectionList(
                    devices = devicesList,
                    selectedDevices = selectedDevices,
                    onDeviceToggle = toggleDevice,
                    onBack = { showingDeviceList = false }
                )
            } else {
                // Показываем список адаптеров
                AdapterList(
                    adaptersState = adaptersState,
                    selectedAdapterId = selectedAdapterId,
                    onAdapterClick = { adapterId, adapterName ->
                        Log.d(TAG, "Selected adapter: $adapterId, loading devices...")
                        currentAdapterId = adapterId
                        onAdapterSelected(adapterId)
                    }
                )
            }
        },
        confirmButton = {
            if (showingDeviceList) {
                Row {
                    TextButton(onClick = { showingDeviceList = false }) {
                        Text("Назад")
                    }
                    TextButton(
                        onClick = { 
                            onAddDevices(selectedDevices.toList())
                            onDismiss()
                        },
                        enabled = selectedDevices.isNotEmpty()
                    ) {
                        Text("Добавить (${selectedDevices.size})")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}

@Composable
fun AdapterList(
    adaptersState: UiState<List<JsonObject>>,
    selectedAdapterId: String? = null,
    onAdapterClick: (adapterId: String, adapterName: String) -> Unit = { _, _ -> }
) {
    when (val state = adaptersState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Загрузка адаптеров...")
                }
            }
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Text("Адаптеры не найдены")
            } else {
                // Извлекаем уникальные ID адаптеров и их состояние alive
                // system.adapter.admin.0.alive -> admin.0
                // system.adapter.tuya.0.upload -> tuya.0
                val adapterMap = mutableMapOf<String, Pair<String, Boolean>>() // (adapterId, adapterName, isAlive)
                
                for (obj in state.data) {
                    val id = obj["_id"]?.toString()?.trim('"') ?: ""
                    if (id.contains("system.adapter.")) {
                        val parts = id.split(".")
                        if (parts.size >= 5) {
                            // system.adapter.name.0.something -> name.0
                            val adapterBaseId = "${parts[2]}.${parts[3]}"
                            
                            // Проверяем, это alive или другой параметр
                            val isAlive = parts.size >= 6 && parts[5] == "alive"
                            val value = obj["val"]?.toString()
                            val isAliveValue = if (isAlive) {
                                value == "true" || value == "1" || value == "True"
                            } else {
                                null
                            }
                            
                            // Имя адаптера = ID адаптера
                            if (!adapterMap.containsKey(adapterBaseId)) {
                                adapterMap[adapterBaseId] = adapterBaseId to (isAliveValue ?: true)
                            } else if (isAlive) {
                                // Обновляем значение alive если нашли
                                adapterMap[adapterBaseId] = adapterMap[adapterBaseId]!!.first to (isAliveValue ?: true)
                            }
                        }
                    }
                }
                
                val adapters = adapterMap.toList()
                Log.d(TAG, "Found ${adapters.size} adapters, selectedAdapterId=$selectedAdapterId")
                
                if (adapters.isEmpty()) {
                    Text("Системные адаптеры не найдены")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(adapters) { (adapterId, adapterInfo) ->
                            val (name, isAlive) = adapterInfo
                            val isSelected = adapterId == selectedAdapterId
                            Log.d(TAG, "Adapter $adapterId ($name): isSelected=$isSelected, selectedAdapterId=$selectedAdapterId")
                            AdapterItem(
                                adapterId = adapterId,
                                adapterName = name,
                                isAlive = isAlive,
                                isSelected = isSelected,
                                onClick = {
                                    onAdapterClick(adapterId, name)
                                }
                            )
                        }
                    }
                }
            }
        }
        is UiState.Error -> {
            Text("Ошибка: ${state.message}")
        }
    }
}

@Composable
fun AdapterItem(
    adapterId: String,
    adapterName: String,
    isAlive: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Индикатор активности
                if (isAlive) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = "Активен",
                        tint = Color(0xFF4CAF50), // Зеленый
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = "Не активен",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
                
                Column {
                    Text(
                        adapterName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Адаптер ioBroker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Выбран",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DeviceSelectionList(
    devices: List<Device>,
    selectedDevices: Set<Device>,
    onDeviceToggle: (Device) -> Unit,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Найдено устройств: ${devices.size}")
            TextButton(onClick = onBack) {
                Text("Назад")
            }
        }
        
        Log.d(TAG, "DeviceSelectionList rendering with ${devices.size} devices")
        
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Устройства не найдены")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(devices) { device ->
                    val isSelected = selectedDevices.contains(device)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceToggle(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
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
                                    device.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    "${device.type} • ${device.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isSelected) 
                                    Icons.Default.CheckCircle 
                                else 
                                    Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                                tint = if (isSelected) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
