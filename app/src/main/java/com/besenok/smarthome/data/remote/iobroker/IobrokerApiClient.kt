package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IobrokerApiClient"

/**
 * Координатор API ioBroker
 * Ответственность: координация сервисов, публичный API для Repository
 * 
 * Зависимости:
 * - IobrokerRestClient: HTTP/WebSocket запросы
 * - AdapterService: управление адаптерами
 * - DeviceParser: парсинг устройств из JSON
 * - DeviceClassifier: классификация устройств
 */
@Singleton
class IobrokerApiClient @Inject constructor(
    private val restClient: IobrokerRestClient,
    private val adapterService: AdapterService,
    private val deviceParser: DeviceParser,
    private val deviceClassifier: DeviceClassifier,
    private val json: Json
) {

    // ==================== HTTP Wrappers (делегируем restClient) ====================

    suspend fun getState(objectId: String): IobrokerState? {
        return restClient.getState(objectId)
    }

    suspend fun setState(objectId: String, value: Any, ack: Boolean = true) {
        restClient.setStateWebSocket(objectId, value, ack)
    }

    suspend fun getObject(objectId: String): JsonObject? {
        return restClient.getObject(objectId)
    }

    suspend fun getObjects(pattern: String = "*", generate: Boolean = false): JsonObject? {
        Log.d(TAG, "getObjects called with pattern: $pattern")
        return restClient.getObjects(pattern, generate)
    }

    suspend fun getStates(pattern: String = "*"): JsonObject? {
        Log.d(TAG, "getStates called with pattern: $pattern")
        return restClient.getStates(pattern)
    }

    // ==================== Adapter Management (делегируем adapterService) ====================

    suspend fun getAdapterList(): List<com.besenok.smarthome.domain.model.AdapterInfo> {
        return adapterService.getAdapterList(restClient)
    }

    // ==================== Device Parsing (делегируем deviceParser + classifier) ====================

    /**
     * Получение устройств из адаптера
     * @param adapterId ID адаптера (например, "mihome.0", "tuya.0")
     * @return Список устройств адаптера
     */
    suspend fun getAdapterDevices(adapterId: String): List<com.besenok.smarthome.domain.model.Device> {
        // Получаем все state объекты адаптера
        val allStates = getStates("${adapterId}.*") ?: return emptyList()

        // Получаем объекты для получения имен
        val allObjects = getObjects("${adapterId}.*")

        return deviceParser.parseAdapterDevices(
            adapterId = adapterId,
            states = allStates,
            objects = allObjects,
            restClient = restClient,
            classifier = deviceClassifier
        )
    }

    // ==================== Utility ====================

    suspend fun enumList(): List<String> {
        return emptyList()
    }

    suspend fun discoverAllDevices(): List<JsonObject> {
        return getObjects("*")?.filterValues { it is JsonObject }?.map { (id, obj) ->
            val jsonObject = obj as JsonObject
            val type = jsonToString(jsonObject, "type")
            if (type == "device") jsonObject else null
        }?.filterNotNull() ?: emptyList()
    }

    private fun jsonToString(obj: JsonObject?, key: String): String? {
        return obj?.get(key)?.toString()?.trim('"')
    }
}
