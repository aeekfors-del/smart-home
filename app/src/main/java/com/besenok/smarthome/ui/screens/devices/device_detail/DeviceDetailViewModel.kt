package com.besenok.smarthome.ui.screens.devices.device_detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.State
import com.besenok.smarthome.domain.usecase.device.GetDevicesUseCase
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
    private val setDeviceStateUseCase: SetDeviceStateUseCase
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
                // Загружаем все устройства и ищем нужное
                val result = getDevicesUseCase()
                when (result) {
                    is UiState.Success -> {
                        val foundDevice = result.data.find { it.id == deviceId }
                        if (foundDevice != null) {
                            Log.d(TAG, "Device found: ${foundDevice.name}")
                            _device.value = UiState.Success(foundDevice)
                        } else {
                            Log.w(TAG, "Device not found: $deviceId")
                            _device.value = UiState.Error("Устройство не найдено")
                        }
                    }
                    is UiState.Error -> {
                        Log.e(TAG, "Error loading device")
                        _device.value = UiState.Error(result.message)
                    }
                    else -> {
                        _device.value = UiState.Error("Ошибка загрузки устройства")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device", e)
                _device.value = UiState.Error(e.message ?: "Ошибка загрузки устройства")
            }
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
