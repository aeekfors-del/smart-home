package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import javax.inject.Inject

class RenameDeviceUseCase @Inject constructor(
    private val dataSource: AppPreferencesDataSource
) {
    suspend operator fun invoke(deviceId: String, newName: String) {
        dataSource.saveDeviceName(deviceId, newName)
    }
}
