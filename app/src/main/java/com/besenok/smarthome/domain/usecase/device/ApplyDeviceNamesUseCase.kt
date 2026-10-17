package com.besenok.smarthome.domain.usecase.device

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.model.Device
import javax.inject.Inject

private const val TAG = "ApplyDeviceNamesUseCase"

class ApplyDeviceNamesUseCase @Inject constructor(
    private val dataSource: AppPreferencesDataSource
) {
    suspend operator fun invoke(devices: List<Device>): List<Device> {
        val customNames = dataSource.getAllDeviceNames()
        Log.d(TAG, "Loaded ${customNames.size} custom names: $customNames")
        
        return devices.map { device ->
            // Ключ в getAllDeviceNames() уже без префикса
            val customName = customNames[device.id]
            if (customName != null && customName.isNotBlank()) {
                Log.d(TAG, "Applied custom name for ${device.id}: '${device.name}' -> '$customName'")
                device.copy(name = customName)
            } else {
                device
            }
        }
    }
}
