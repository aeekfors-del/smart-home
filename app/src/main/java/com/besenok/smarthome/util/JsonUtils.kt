package com.besenok.smarthome.util

import kotlinx.serialization.json.JsonObject

/**
 * Общие утилиты для работы с JSON
 */
object JsonUtils {

    /**
     * Извлекает строковое значение из JsonObject по ключу
     */
    fun getString(obj: JsonObject?, key: String): String? {
        return obj?.get(key)?.toString()?.trim('"')
    }

    /**
     * Парсит строковое значение в подходящий Kotlin тип
     */
    fun parseValue(str: String?): Any? {
        if (str == null) return null
        return when (str) {
            "true" -> true
            "false" -> false
            else -> try {
                str.toInt()
            } catch (e: NumberFormatException) {
                try {
                    str.toDouble()
                } catch (e: NumberFormatException) {
                    str
                }
            }
        }
    }
}
