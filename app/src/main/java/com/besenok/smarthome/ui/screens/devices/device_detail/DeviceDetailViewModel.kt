package com.besenok.smarthome.ui.screens.devices.device_detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.State
import com.besenok.smarthome.domain.usecase.device.GetDevicesUseCase
import com.besenok.smarthome.domain.usecase.device.RenameParameterUseCase
import com.besenok.smarthome.domain.usecase.device.SetDeviceStateUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceDetailViewModel"

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val setDeviceStateUseCase: SetDeviceStateUseCase,
    private val renameParameterUseCase: RenameParameterUseCase,
    private val getDevicesFromAdapterUseCase: com.besenok.smarthome.domain.usecase.adapter.GetDevicesFromAdapterUseCase
) : ViewModel() {

    private val _device = MutableStateFlow<UiState<Device>>(UiState.Loading)
    val device: StateFlow<UiState<Device>> = _device.asStateFlow()

    private val _setStateState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val setStateState: StateFlow<UiState<Unit>> = _setStateState.asStateFlow()

    fun loadDevice(deviceId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading device: $deviceId")
            _device.value = UiState.Loading
            
            try {
                // Сначала пытаемся найти в уже загруженных
                val result = getDevicesUseCase()
                Log.d(TAG, "GetDevices result: ${result}")
                
                when (result) {
                    is UiState.Success -> {
                        val foundDevice = result.data.find { it.id == deviceId }
                        if (foundDevice != null) {
                            Log.d(TAG, "Device found in cache: ${foundDevice.name}")
                            _device.value = UiState.Success(foundDevice)
                            return@launch
                        }
                    }
                    is UiState.Error -> {
                        Log.e(TAG, "Error in getDevices: ${result.message}")
                    }
                    else -> {}
                }
                
                // Если не найдено, загружаем напрямую через getStates
                Log.d(TAG, "Device not in cache, loading directly from ioBroker...")
                loadDeviceDirectly(deviceId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device", e)
                _device.value = UiState.Error(e.message ?: "Ошибка загрузки устройства")
            }
        }
    }
    
    /**
     * Загружает устройство напрямую из ioBroker API
     */
    private suspend fun loadDeviceDirectly(deviceId: String) {
        try {
            // Получаем adapterId из deviceId
            val adapterId = extractAdapterIdFromDeviceId(deviceId)
            if (adapterId == null) {
                Log.w(TAG, "Cannot extract adapterId from deviceId: $deviceId")
                _device.value = UiState.Error("Невозможно загрузить устройство")
                return
            }
            
            Log.d(TAG, "Loading device directly from adapter: $adapterId")
            
            // Fallback: создаём простое устройство из deviceId
            Log.d(TAG, "Creating device from deviceId: $deviceId")
            val device = com.besenok.smarthome.domain.model.Device(
                id = deviceId,
                name = deviceId.split(".").lastOrNull() ?: deviceId,
                type = com.besenok.smarthome.domain.model.Device.DeviceType.OTHER,
                controlType = com.besenok.smarthome.domain.model.ControlType.NONE,
                state = null,
                channels = emptyList(),
                connectionGroup = adapterId
            )
            
            _device.value = UiState.Success(device)
            Log.d(TAG, "Device loaded: ${device.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading device directly", e)
            _device.value = UiState.Error("Ошибка загрузки: ${e.message}")
        }
    }
    
    /**
     * Извлекает adapterId из ID устройства
     * mihome.0.devices.gateway_286c07f0aa08 -> mihome.0
     * tuya.0.bf91e1cc5543e02434vkvs -> tuya.0
     * mqtt.0.home -> mqtt.0
     */
    private fun extractAdapterIdFromDeviceId(deviceId: String): String? {
        return try {
            val parts = deviceId.split(".")
            if (parts.size >= 3 && parts[0] == "system" && parts[1] == "adapter") {
                "${parts[2]}.${parts[3]}"
            } else if (parts.size >= 3) {
                // Формат: adapterName.index.something
                "${parts[0]}.${parts[1]}"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract adapterId from: $deviceId", e)
            null
        }
    }

    fun updateState(channelId: String, value: Any) {
        viewModelScope.launch {
            Log.d(TAG, "Updating state: $channelId = $value")
            _setStateState.value = UiState.Loading
            try {
                setDeviceStateUseCase(channelId, value)
                _setStateState.value = UiState.Success(Unit)
                Log.d(TAG, "State updated successfully, reloading device...")
                
                // После успешной отправки, перезагружаем устройства для получения актуального состояния
                val result = getDevicesUseCase()
                if (result is UiState.Success) {
                    val currentDevice = (_device.value as? UiState.Success)?.data
                    if (currentDevice != null) {
                        val updatedDevice = result.data.find { it.id == currentDevice.id }
                        if (updatedDevice != null) {
                            Log.d(TAG, "Device state updated: ${updatedDevice.channels.size} channels")
                            _device.value = UiState.Success(updatedDevice)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating state", e)
                _setStateState.value = UiState.Error(e.message ?: "Ошибка обновления состояния")
            }
        }
    }

    /**
     * Переименовывает параметр (канал) устройства.
     * Пустое имя сбрасывает пользовательское название к оригинальному.
     */
    fun renameParameter(objectId: String, newName: String) {
        viewModelScope.launch {
            try {
                renameParameterUseCase(objectId, newName)
                val current = (_device.value as? UiState.Success)?.data ?: return@launch
                val fallbackName = objectId.substringAfterLast(".")
                _device.value = UiState.Success(
                    current.copy(
                        channels = current.channels.map { channel ->
                            if (channel.id == objectId) {
                                channel.copy(name = newName.ifBlank { fallbackName })
                            } else {
                                channel
                            }
                        }
                    )
                )
                Log.d(TAG, "Parameter $objectId renamed to '$newName'")
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming parameter $objectId", e)
            }
        }
    }

    fun toggleChannel(channel: Device.Channel) {
        viewModelScope.launch {
            when (channel.controlType) {
                ControlType.SWITCH -> {
                    val currentValue = channel.state?.value
                    val newValue = when (currentValue) {
                        is Boolean -> !currentValue
                        is String -> if (currentValue.lowercase() == "false" || currentValue == "0") "true" else "false"
                        is Number -> if (currentValue.toInt() == 0) 1 else 0
                        else -> "true"
                    }
                    updateState(channel.id, newValue)
                }
                else -> {
                    updateState(channel.id, channel.state?.value ?: 0)
                }
            }
        }
    }
}
