package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IobrokerApiService"

@Singleton
class IobrokerApiService @Inject constructor(
    private val wsClient: IobrokerWebSocketClient,
    private val json: Json
) {

    suspend fun getState(objectId: String): IobrokerState? {
        return wsClient.getState(objectId)
    }

    suspend fun setState(objectId: String, value: Any, ack: Boolean = true) {
        wsClient.setStateWebSocket(objectId, value, ack)
    }

    suspend fun getObject(objectId: String): JsonObject? {
        return wsClient.getObject(objectId)
    }

    suspend fun getObjects(pattern: String = "*", generate: Boolean = false): JsonObject? {
        Log.d(TAG, "getObjects called with pattern: $pattern")
        return wsClient.getObjects(pattern, generate)
    }

    /**
     * Получение состояний по паттерну через REST API
     * Endpoint: GET /states?pattern=X
     * Возвращает: { "stateId1": {val, ack, ts, ...}, "stateId2": {...} }
     */
    suspend fun getStates(pattern: String = "*"): JsonObject? {
        Log.d(TAG, "getStates called with pattern: $pattern")
        return wsClient.getStates(pattern)
    }

    /**
     * Получение дерева объектов с REST API
     * ioBroker REST API: /getObjects
     * Возвращает: { "id1": {...}, "id2": {...}, ... }
     */
    suspend fun getObjectTree(prefix: String? = null): List<JsonObject> {
        val pattern = prefix ?: "*"
        Log.d(TAG, "Getting object tree with pattern: $pattern")
        val response = getObjects(pattern, generate = false)
        Log.d(TAG, "Response: ${response?.keys?.take(20)}")
        
        // ioBroker REST API /getObjects returns:
        // { "id1": { _id, type, common, native, val, ack, ts, ... }, ... }
        val result = response
        Log.d(TAG, "Result type: ${result?.javaClass?.simpleName}")
        Log.d(TAG, "Result keys: ${result?.keys?.take(20)}")
        
        return result?.let { res ->
            if (res is JsonObject) {
                Log.d(TAG, "Result has ${res.size} entries")
                val devices = mutableListOf<JsonObject>()
                for ((key, value) in res) {
                    Log.d(TAG, "Key: $key, Value type: ${value.javaClass.simpleName}")
                    if (value is JsonObject) {
                        Log.d(TAG, "  Value keys: ${value.keys.take(15)}")
                        Log.d(TAG, "  _id: ${value["_id"]}")
                        Log.d(TAG, "  val: ${value["val"]}")
                        Log.d(TAG, "  common: ${value["common"]?.javaClass?.simpleName}")
                        devices.add(value)
                    }
                }
                Log.d(TAG, "Extracted ${devices.size} objects")
                devices
            } else {
                Log.w(TAG, "Result is not JsonObject: ${res?.javaClass?.simpleName}")
                emptyList()
            }
        } ?: run {
            Log.w(TAG, "Result is null!")
            emptyList()
        }
    }

    suspend fun enumList(): List<String> {
        return emptyList()
    }

    suspend fun adapterList(): List<JsonObject> {
        Log.d(TAG, "Getting adapter list from system.adapter.*")
        return getObjectTree("system.adapter.*")
    }

    /**
     * Получение устройств из адаптера
     * @param adapterId ID адаптера (например, "mihome.0", "tuya.0")
     * @return Список устройств адаптера
     */
    suspend fun getDevicesFromAdapter(adapterId: String): List<JsonObject> {
        Log.d(TAG, "Getting devices from adapter: $adapterId")
        // Пробуем несколько возможных паттернов для получения устройств
        val patterns = listOf(
            "$adapterId.devices.*",
            "system.adapter.$adapterId.devices.*",
            "$adapterId.*",
            "system.adapter.$adapterId.*"
        )
        
        for (pattern in patterns) {
            Log.d(TAG, "Trying pattern: $pattern")
            val result = getObjectTree(pattern)
            Log.d(TAG, "Pattern $pattern returned ${result.size} objects")
            if (result.isNotEmpty()) {
                Log.d(TAG, "First object: ${result.firstOrNull()}")
                return result
            }
        }
        
        Log.w(TAG, "No devices found with any pattern for adapter $adapterId")
        return emptyList()
    }

    suspend fun discoverAllDevices(): List<JsonObject> {
        return getObjectTree("device")
    }
}
