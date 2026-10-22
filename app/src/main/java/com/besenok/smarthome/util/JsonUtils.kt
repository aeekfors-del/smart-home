package com.besenok.smarthome.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Общие утилиты для работы с JSON
 */
object JsonUtils {

    /**
     * Извлекает строковое значение из JsonObject по ключу.
     * Без промежуточных String/trim и без исключений.
     */
    fun getString(obj: JsonObject?, key: String): String? {
        val element = obj?.get(key) as? JsonPrimitive ?: return null
        return element.content
    }

    /**
     * Парсит строковое значение в подходящий Kotlin тип
     */
    fun parseValue(str: String?): Any? {
        if (str == null) return null
        return when (str) {
            "true" -> true
            "false" -> false
            else -> str.toIntOrNull() ?: str.toDoubleOrNull() ?: str
        }
    }
}
