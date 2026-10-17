package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import javax.inject.Inject

class GetDeviceNameUseCase @Inject constructor(
    private val dataSource: AppPreferencesDataSource
) {
    suspend operator fun invoke(deviceId: String): String? {
        return dataSource.getCustomDeviceName(deviceId)
    }
}
