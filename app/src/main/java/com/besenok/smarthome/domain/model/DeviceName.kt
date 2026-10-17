package com.besenok.smarthome.domain.model

/**
 * Модель для хранения соответствия между оригинальным и пользовательским именами устройства
 */
data class DeviceName(
    val deviceId: String,
    val originalName: String,  // Имя от брокера
    var customName: String? = null  // Пользовательское имя (nullable = использовать оригинал)
) {
    /**
     * Возвращает имя для отображения: пользовательское если есть, иначе оригинальное
     */
    fun getDisplayName(): String = customName ?: originalName
}
