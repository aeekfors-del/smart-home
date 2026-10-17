package com.besenok.smarthome.ui.screens.devices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.usecase.device.ApplyDeviceNamesUseCase
import com.besenok.smarthome.domain.usecase.device.GetDevicesUseCase
import com.besenok.smarthome.domain.usecase.device.RenameDeviceUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceListViewModel"

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val renameDeviceUseCase: RenameDeviceUseCase,
    private val applyDeviceNamesUseCase: ApplyDeviceNamesUseCase
) : ViewModel() {

    private val _devices = MutableStateFlow<UiState<List<Device>>>(UiState.Loading)
    val devices: StateFlow<UiState<List<Device>>> = _devices.asStateFlow()

    private val _groups = MutableStateFlow<List<DeviceGroup>>(emptyList())
    val groups: StateFlow<List<DeviceGroup>> = _groups.asStateFlow()

    private val _selectedFilter = MutableStateFlow(DeviceFilter.ALL)
    val selectedFilter: StateFlow<DeviceFilter> = _selectedFilter.asStateFlow()

    // Состояние для диалога переименования
    private val _editingDevice = MutableStateFlow<Device?>(null)
    val editingDevice: StateFlow<Device?> = _editingDevice.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            Log.d(TAG, "Loading devices...")
            try {
                val result = getDevicesUseCase()
                Log.d(TAG, "Devices loaded: $result")
                
                // Применяем пользовательские имена к устройствам
                val devicesWithNames = when (result) {
                    is UiState.Success -> {
                        applyDeviceNamesUseCase(result.data)
                    }
                    else -> emptyList()
                }
                
                _devices.value = result
                
                // Группируем устройства по connectionGroup
                val groupedDevices = groupDevicesByConnection(UiState.Success(devicesWithNames))
                _groups.value = groupedDevices
                Log.d(TAG, "Created ${groupedDevices.size} device groups")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading devices", e)
                _devices.value = UiState.Error(e.message ?: "Failed to load devices")
            }
        }
    }

    fun toggleGroup(groupId: String) {
        val updatedGroups = _groups.value.map { group ->
            if (group.id == groupId) {
                group.copy(isExpanded = !group.isExpanded)
            } else {
                group
            }
        }
        _groups.value = updatedGroups
        Log.d(TAG, "Toggled group $groupId: ${updatedGroups.find { it.id == groupId }?.isExpanded}")
    }

    fun setFilter(filter: DeviceFilter) {
        _selectedFilter.value = filter
    }

    // Методы для переименования устройств
    fun startRenameDevice(device: Device) {
        _editingDevice.value = device
        Log.d(TAG, "Start rename device: ${device.name} (${device.id})")
    }

    fun confirmRenameDevice(newName: String) {
        val device = _editingDevice.value ?: return
        if (newName.isBlank()) return
        
        viewModelScope.launch {
            Log.d(TAG, "Renaming device ${device.id} from '${device.name}' to '$newName'")
            try {
                renameDeviceUseCase(device.id, newName)
                _editingDevice.value = null
                loadDevices()  // Перезагружаем для применения нового имени
                Log.d(TAG, "Device renamed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming device", e)
            }
        }
    }

    fun cancelRenameDevice() {
        _editingDevice.value = null
        Log.d(TAG, "Cancel rename device")
    }

    private fun groupDevicesByConnection(
        uiState: UiState<List<Device>>
    ): List<DeviceGroup> {
        val deviceList = when (uiState) {
            is UiState.Success -> uiState.data
            else -> emptyList()
        }

        // Фильтруем по выбранному фильтру
        val filteredDevices = when (_selectedFilter.value) {
            DeviceFilter.ALL -> deviceList
            DeviceFilter.LIGHT -> deviceList.filter { it.type == Device.DeviceType.LIGHT }
            DeviceFilter.SOCKET -> deviceList.filter { it.type == Device.DeviceType.SOCKET }
            DeviceFilter.SENSOR -> deviceList.filter { it.type == Device.DeviceType.SENSOR }
            DeviceFilter.GATE -> deviceList.filter { it.type == Device.DeviceType.GATE }
            DeviceFilter.BLIND -> deviceList.filter { it.type == Device.DeviceType.BLIND }
            DeviceFilter.OTHER -> deviceList.filter { it.type == Device.DeviceType.OTHER }
        }

        // Группируем по connectionGroup
        return filteredDevices
            .groupBy { it.connectionGroup }
            .map { (groupId, devices) ->
                val groupName = formatGroupName(groupId)
                DeviceGroup(
                    id = groupId,
                    name = groupName,
                    devices = devices,
                    isExpanded = true
                )
            }
            .sortedBy { it.id } // mihome.0 будет первым
    }

    private fun formatGroupName(groupId: String): String {
        return when (groupId) {
            "mihome.0" -> "Xiaomi Mi Home"
            "tuya.0" -> "Tuya"
            else -> groupId
        }
    }

    val filteredDevices: List<Device>
        get() {
            val deviceList = when (val state = _devices.value) {
                is UiState.Success -> state.data
                else -> emptyList()
            }

            return when (_selectedFilter.value) {
                DeviceFilter.ALL -> deviceList
                DeviceFilter.LIGHT -> deviceList.filter { it.type == Device.DeviceType.LIGHT }
                DeviceFilter.SOCKET -> deviceList.filter { it.type == Device.DeviceType.SOCKET }
                DeviceFilter.SENSOR -> deviceList.filter { it.type == Device.DeviceType.SENSOR }
                DeviceFilter.GATE -> deviceList.filter { it.type == Device.DeviceType.GATE }
                DeviceFilter.BLIND -> deviceList.filter { it.type == Device.DeviceType.BLIND }
                DeviceFilter.OTHER -> deviceList.filter { it.type == Device.DeviceType.OTHER }
            }
        }

    enum class DeviceFilter(val label: String) {
        ALL("Все"),
        LIGHT("Свет"),
        SOCKET("Розетки"),
        SENSOR("Датчики"),
        GATE("Ворота"),
        BLIND("Шторы"),
        OTHER("Другое")
    }
}
