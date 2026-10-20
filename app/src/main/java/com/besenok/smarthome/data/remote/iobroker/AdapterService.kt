package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.util.JsonUtils
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdapterService"

/**
 * Сервис для работы с адаптерами ioBroker
 * Ответственность: получение списка адаптеров и их статусов
 */
@Singleton
class AdapterService @Inject constructor() {

    /**
     * Получение списка адаптеров с проверкой статуса alive
     * Извлекает adapterId из ID объектов и проверяет system.adapter.XXX.0.alive
     */
    suspend fun getAdapterList(restClient: IobrokerRestClient): List<AdapterInfo> {
        Log.d(TAG, "========== GET ADAPTER LIST ==========")

        // Получаем все объекты system.adapter.* чтобы извлечь уникальные adapterId
        val allObjectsResponse = restClient.getObjects("system.adapter.*", generate = false) ?: return emptyList()

        Log.d(TAG, "Total objects from getObjects: ${allObjectsResponse.size}")

        // Извлекаем уникальные adapterId из всех объектов (например, "admin.0", "mihome.0")
        val adapterIds = mutableSetOf<String>()
        for ((objId, objValue) in allObjectsResponse) {
            if (objValue !is JsonObject) continue

            // Извлекаем adapterId из ID объекта
            val extractedAdapterId = extractAdapterId(objId)
            if (extractedAdapterId != null) {
                adapterIds.add(extractedAdapterId)
            }
        }

        Log.d(TAG, "Extracted ${adapterIds.size} unique adapter IDs: ${adapterIds.joinToString(", ")}")

        // Получаем состояния alive
        val statesResponse = restClient.getStates("system.adapter.*.alive")
        Log.d(TAG, "States for *.alive: ${statesResponse?.size} entries")
        Log.d(TAG, "statesResponse type: ${statesResponse?.javaClass?.simpleName}")

        // Собираем информацию об адаптерах
        val adaptersMap = mutableMapOf<String, AdapterInfo>()

        for (adapterId in adapterIds) {
            val aliveStateKey = "system.adapter.$adapterId.alive"

            Log.d(TAG, "  Checking adapter $adapterId:")
            Log.d(TAG, "    aliveStateKey='$aliveStateKey'")
            Log.d(TAG, "    keys contains: ${aliveStateKey in (statesResponse?.keys ?: emptySet())}")

            // Проверяем существует ли alive статус для этого адаптера
            val aliveState = statesResponse?.get(aliveStateKey) as? JsonObject

            if (aliveState == null) {
                Log.w(TAG, "  ⚠ Adapter $adapterId has NO alive state - skipping")
                continue
            }

            // Получаем значение alive
            val valField = aliveState["val"]
            val alive = valField.toString() == "1" || valField.toString().equals("true", ignoreCase = true)

            Log.d(TAG, "  ✓ Adapter $adapterId: alive=$alive (val=$valField)")

            // Ищем имя адаптера в объектах
            val adapterName = findAdapterName(allObjectsResponse, adapterId)

            adaptersMap[adapterId] = AdapterInfo(
                id = adapterId,
                name = adapterName,
                alive = alive,
                connected = false,
                devicesCount = 0
            )
        }

        // Возвращаем все найденные адаптеры
        val result = adaptersMap.values.toList()
        Log.d(TAG, "✓ Found ${result.size} adapters: ${result.map { it.id }.joinToString(", ")}")
        Log.d(TAG, "========== END GET ADAPTER LIST ==========")
        return result
    }

    /**
     * Ищет имя адаптера среди объектов
     */
    private fun findAdapterName(objectsResponse: JsonObject, adapterId: String): String {
        // Ищем объект с _id содержащим adapterId и поле common.name
        val searchPattern = "system.adapter.$adapterId"

        for ((objId, objValue) in objectsResponse) {
            if (!objId.contains(searchPattern)) continue
            if (objValue !is JsonObject) continue

            val common = objValue["common"] as? JsonObject
            val name = JsonUtils.getString(common, "name")

            if (!name.isNullOrBlank()) {
                return name
            }
        }

        // Fallback: используем adapterId как имя
        return adapterId.split(".").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Извлечение adapterId из ID объекта
     * system.adapter.admin.0.0.alive -> admin.0
     * system.adapter.tuya.0.meta -> tuya.0
     */
    fun extractAdapterId(objId: String): String? {
        return try {
            val parts = objId.split(".")
            // system.adapter.XXX.0 -> parts[0]=system, parts[1]=adapter, parts[2]=name, parts[3]=index
            if (parts.size >= 4 && parts[0] == "system" && parts[1] == "adapter") {
                "${parts[2]}.${parts[3]}"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract adapterId from: $objId", e)
            null
        }
    }
}
