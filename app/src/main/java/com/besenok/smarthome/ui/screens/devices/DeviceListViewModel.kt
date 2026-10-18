package com.besenok.smarthome.ui.screens.devices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.usecase.adapter.GetAdaptersUseCase
import com.besenok.smarthome.domain.usecase.adapter.GetDevicesFromAdapterUseCase
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
    private val applyDeviceNamesUseCase: ApplyDeviceNamesUseCase,
    private val getAdaptersUseCase: GetAdaptersUseCase,
    private val getDevicesFromAdapterUseCase: GetDevicesFromAdapterUseCase,
    private val settingsDataSource: AppPreferencesDataSource
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
    
    // Состояние для диалога добавления устройства
    private val _showAddDeviceDialog = MutableStateFlow(false)
    val showAddDeviceDialog: StateFlow<Boolean> = _showAddDeviceDialog.asStateFlow()
    
    private val _adaptersState = MutableStateFlow<UiState<List<AdapterInfo>>>(UiState.Loading)
    val adaptersState: StateFlow<UiState<List<AdapterInfo>>> = _adaptersState.asStateFlow()
    
    private val _selectedAdapterId = MutableStateFlow<String?>(null)
    val selectedAdapterId: StateFlow<String?> = _selectedAdapterId.asStateFlow()
    
    private val _devicesFromAdapter = MutableStateFlow<UiState<List<Device>>>(UiState.Success(emptyList()))
    val devicesFromAdapter: StateFlow<UiState<List<Device>>> = _devicesFromAdapter.asStateFlow()
    
    private val _addedDeviceIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())
    val addedDeviceIds: StateFlow<MutableSet<String>> = _addedDeviceIds.asStateFlow()

    init {
        // Загружаем устройства из сохранённых адаптеров
        viewModelScope.launch {
            val addedAdapters = settingsDataSource.getAddedAdapterIds()
            Log.d(TAG, "ViewModel init: loaded ${addedAdapters.size} adapters from DataStore")
            if (addedAdapters.isNotEmpty()) {
                loadDevices()
            } else {
                Log.d(TAG, "No adapters added yet, showing empty state")
                // Устанавливаем пустой список вместо Loading
                _devices.value = UiState.Success(emptyList())
                _groups.value = emptyList()
            }
        }
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
    
    // Методы для добавления устройства
    fun showAddDeviceDialog() {
        _showAddDeviceDialog.value = true
        _devicesFromAdapter.value = UiState.Success(emptyList())
        _addedDeviceIds.value = mutableSetOf()
        loadAdapters()
        Log.d(TAG, "Show add device dialog")
    }
    
    fun hideAddDeviceDialog() {
        _showAddDeviceDialog.value = false
        Log.d(TAG, "Hide add device dialog")
    }
    
    fun loadDevicesFromAdapter(adapterId: String) {
        Log.d(TAG, "loadDevicesFromAdapter called with: $adapterId")
        _selectedAdapterId.value = adapterId
        _devicesFromAdapter.value = UiState.Loading
        viewModelScope.launch {
            Log.d(TAG, "Loading devices from adapter: $adapterId")
            try {
                val result = getDevicesFromAdapterUseCase(adapterId)
                _devicesFromAdapter.value = result
                val deviceCount = when (result) {
                    is UiState.Success -> result.data?.size ?: 0
                    else -> 0
                }
                Log.d(TAG, "Loaded $deviceCount devices from adapter $adapterId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading devices from adapter", e)
                _devicesFromAdapter.value = UiState.Error(e.message ?: "Ошибка загрузки устройств")
            }
        }
    }
    
    fun addSelectedDevices(devices: List<Device>) {
        Log.d(TAG, "Adding ${devices.size} devices from adapter")
        if (devices.isEmpty()) {
            Log.w(TAG, "No devices selected to add")
            return
        }
        
        viewModelScope.launch {
            // Сохраняем deviceIds каждого добавленного устройства
            for (device in devices) {
                settingsDataSource.addDeviceToList(device.id)
                Log.d(TAG, "Saved device ${device.id} to DataStore")
            }
            
            // Также сохраняем adapterId для загрузки всех устройств адаптера
            val adapterId = devices.firstOrNull()?.connectionGroup
            if (adapterId != null) {
                settingsDataSource.addAdapterToList(adapterId)
                Log.d(TAG, "Saved adapter $adapterId to DataStore")
            }
            
            // Скрываем диалог
            hideAddDeviceDialog()
            
            // Перезагружаем устройства
            loadDevices()
            
            Log.d(TAG, "Devices added successfully")
        }
    }
    
    fun removeDevice(device: Device) {
        Log.d(TAG, "Removing device: ${device.id} from ${device.connectionGroup}")
        
        viewModelScope.launch {
            try {
                // Удаляем device из списка добавленных
                settingsDataSource.removeDeviceFromList(device.id)
                Log.d(TAG, "Removed device ${device.id} from DataStore")
                
                // Перезагружаем устройства
                loadDevices()
                
                Log.d(TAG, "Device removed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing device", e)
            }
        }
    }
    
    fun loadAdapters() {
        viewModelScope.launch {
            Log.d(TAG, "Loading adapters...")
            try {
                val result = getAdaptersUseCase()
                _adaptersState.value = result
                Log.d(TAG, "Adapters loaded: ${result}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading adapters", e)
                _adaptersState.value = UiState.Error(e.message ?: "Ошибка загрузки адаптеров")
            }
        }
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
                    isExpanded = false  // Группы свёрнуты по умолчанию
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
