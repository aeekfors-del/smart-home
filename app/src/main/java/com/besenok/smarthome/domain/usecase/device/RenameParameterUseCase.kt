package com.besenok.smarthome.domain.usecase.device

import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import javax.inject.Inject

/**
 * Переименование параметра (канала) устройства.
 * Пустое имя сбрасывает пользовательское название к оригинальному.
 */
class RenameParameterUseCase @Inject constructor(
    private val dataSource: AppPreferencesDataSource
) {
    suspend operator fun invoke(objectId: String, newName: String) {
        if (newName.isBlank()) {
            dataSource.deleteParameterName(objectId)
        } else {
            dataSource.saveParameterName(objectId, newName)
        }
    }
}
