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
import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.ui.common.UiState

private const val TAG = "AddDeviceDialog"

/**
 * Диалог выбора адаптера для добавления устройств
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    adaptersState: UiState<List<AdapterInfo>>,
    devicesState: UiState<List<Device>>,
    onAddDevices: (List<Device>) -> Unit,
    onSelectedDevicesChanged: (List<Device>) -> Unit,
    selectedAdapterId: String? = null,
    onAdapterSelected: (String) -> Unit = {}
) {
    var selectedDevices by remember { mutableStateOf(mutableSetOf<Device>()) }
    var showingDeviceList by remember { mutableStateOf(false) }
    var currentAdapterId by remember { mutableStateOf<String?>(null) }
    
    // Сбрасываем выбранные устройства при открытии диалога
    LaunchedEffect(showingDeviceList) {
        if (showingDeviceList) {
            selectedDevices.clear()
            Log.d(TAG, "Cleared selected devices when showing device list")
        }
    }
    
    // Сбрасываем выбранные устройства при смене адаптера
    LaunchedEffect(currentAdapterId) {
        if (currentAdapterId != null) {
            selectedDevices.clear()
            Log.d(TAG, "Cleared selected devices for adapter: $currentAdapterId")
        }
    }
    
    // Собираем устройства в список для отображения
    val devicesList = when (val state = devicesState) {
        is UiState.Success -> {
            state.data
        }
        is UiState.Loading -> {
            null
        }
        is UiState.Error -> {
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
        // Ищем устройство по ID, а не по объекту
        val existingDevice = newSet.find { it.id == device.id }
        if (existingDevice != null) {
            newSet.remove(existingDevice)
            Log.d(TAG, "Removed device: ${device.id}")
        } else {
            newSet.add(device)
            Log.d(TAG, "Added device: ${device.id}")
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
    adaptersState: UiState<List<AdapterInfo>>,
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
            val adapters = state.data
            
            if (adapters.isEmpty()) {
                Text("Адаптеры не найдены")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(adapters, key = { it.id }) { adapter ->
                        val isSelected = adapter.id == selectedAdapterId
                        
                        AdapterItem(
                            adapterId = adapter.id,
                            adapterName = adapter.name,
                            isAlive = adapter.alive,
                            isSelected = isSelected,
                            onClick = {
                                onAdapterClick(adapter.id, adapter.name)
                            }
                        )
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
