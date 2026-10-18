package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
     * Получение списка адаптеров с проверкой статуса alive
     * Извлекает adapterId из ID объектов и проверяет system.adapter.XXX.0.alive
     */
    suspend fun getAdapterList(): List<com.besenok.smarthome.domain.model.AdapterInfo> {
        Log.d(TAG, "========== GET ADAPTER LIST ==========")
        
        // Получаем все объекты system.adapter.* чтобы извлечь уникальные adapterId
        val allObjectsResponse = getObjects("system.adapter.*", generate = false) ?: return emptyList()
        
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
        val statesResponse = getStates("system.adapter.*.alive")
        Log.d(TAG, "States for *.alive: ${statesResponse?.size} entries")
        Log.d(TAG, "statesResponse type: ${statesResponse?.javaClass?.simpleName}")
        
        // Собираем информацию об адаптерах
        val adaptersMap = mutableMapOf<String, com.besenok.smarthome.domain.model.AdapterInfo>()
        
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
            val alive = valField?.toString() == "1" || valField?.toString().equals("true", ignoreCase = true)
            
            Log.d(TAG, "  ✓ Adapter $adapterId: alive=$alive (val=$valField)")
            
            // Ищем имя адаптера в объектах
            val adapterName = findAdapterName(allObjectsResponse, adapterId)
            
            adaptersMap[adapterId] = com.besenok.smarthome.domain.model.AdapterInfo(
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
            val name = getJsonString(common, "name")
            
            if (!name.isNullOrBlank()) {
                return name
            }
        }
        
        // Fallback: используем adapterId как имя
        return adapterId.split(".").joinToString(" ") { it.capitalize() }
    }
    
    /**
     * Получение устройств из адаптера
     * @param adapterId ID адаптера (например, "mihome.0", "tuya.0")
     * @return Список устройств адаптера
     */
    suspend fun getAdapterDevices(adapterId: String): List<com.besenok.smarthome.domain.model.Device> {
        Log.d(TAG, "========== GET ADAPTER DEVICES ==========")
        Log.d(TAG, "adapterId: $adapterId")
        
        // Получаем все state объекты адаптера
        val allStates = getStates("${adapterId}.*") ?: return emptyList()
        
        Log.d(TAG, "Total states for ${adapterId}.*: ${allStates.size}")
        
        // Получаем объекты для получения имен
        val allObjects = getObjects("${adapterId}.*") ?: emptyMap()
        
        // Группируем states по устройствам
        val deviceGroups = mutableMapOf<String, MutableList<Pair<String, JsonObject>>>()
        
        for ((stateId, stateValue) in allStates) {
            if (stateValue !is JsonObject) continue
            
            // Извлекаем базовый ID устройства
            val deviceBaseId = extractDeviceBaseId(stateId, adapterId)
            if (deviceBaseId != null) {
                deviceGroups.getOrPut(deviceBaseId) { mutableListOf() }.add(stateId to stateValue)
                Log.d(TAG, "  State: $stateId -> Device: $deviceBaseId")
            }
        }
        
        Log.d(TAG, "Found ${deviceGroups.size} unique devices")
        
        // Преобразуем в Device объекты
        val devices = deviceGroups.map { (deviceBaseId, stateList) ->
            // Получаем объект устройства для имени
            val deviceObj = allObjects[deviceBaseId] as? JsonObject
            val deviceName = extractDeviceNameFromObject(deviceBaseId, deviceObj, adapterId)
            val deviceType = mapDeviceType(deviceName)
            
            // Создаём каналы из состояний
            val channels = stateList.map { (stateId, stateObj) ->
                val common = stateObj["common"] as? JsonObject
                val stateName = getJsonString(common, "name") ?: getJsonString(common, "role") ?: stateId.substringAfterLast(".")
                val stateRole = getJsonString(common, "role") ?: ""
                
                val controlType = mapControlType(stateRole)
                
                // Получаем текущее значение
                val value = stateObj["val"]?.toString()?.let { parseJsonValue(it) }
                val ack = stateObj["ack"]?.toString()?.toBoolean() ?: false
                val ts = stateObj["ts"]?.toString()?.toLongOrNull() ?: 0
                val unit = getJsonString(common, "unit")
                
                Log.d(TAG, "    Channel: $stateName (role=$stateRole) = $value $unit")
                
                com.besenok.smarthome.domain.model.Device.Channel(
                    id = stateId,
                    name = stateName,
                    controlType = controlType,
                    state = com.besenok.smarthome.domain.model.State(
                        value = value,
                        ack = ack,
                        timestamp = ts,
                        unit = unit
                    )
                )
            }
            
            Log.d(TAG, "  ✓ Device: $deviceName (type=${deviceType.name}, channels=${channels.size})")
            
            com.besenok.smarthome.domain.model.Device(
                id = deviceBaseId,
                name = deviceName,
                type = deviceType,
                controlType = when (deviceType) {
                    com.besenok.smarthome.domain.model.Device.DeviceType.LIGHT -> com.besenok.smarthome.domain.model.ControlType.SWITCH
                    else -> com.besenok.smarthome.domain.model.ControlType.NONE
                },
                state = channels.firstOrNull { it.state?.value != null }?.state,
                channels = channels,
                connectionGroup = adapterId
            )
        }
        
        Log.d(TAG, "========== END GET ADAPTER DEVICES: ${devices.size} devices ==========")
        return devices
    }
    
    /**
     * Извлечение adapterId из ID объекта
     * system.adapter.admin.0.0.alive -> admin.0
     * system.adapter.tuya.0.meta -> tuya.0
     */
    private fun extractAdapterId(objId: String): String? {
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
    
    /**
     * Извлечение базового ID устройства из state ID
     * mihome.0.devices.gateway_286c07f0aa08.illumination -> mihome.0.devices.gateway_286c07f0aa08
     * tuya.0.bf2a0780f7abcde10flvxn.42 -> tuya.0.bf2a0780f7abcde10flvxn
     */
    private fun extractDeviceBaseId(stateId: String, adapterId: String): String? {
        // Формат: adapterId.devices.deviceType_id.stateName
        val devicePattern = "${adapterId}.devices."
        
        return if (stateId.startsWith(devicePattern)) {
            val afterDevices = stateId.substring(devicePattern.length)
            val parts = afterDevices.split(".")
            
            if (parts.size > 1) {
                // deviceType_id.stateName -> deviceType_id
                "${devicePattern}${parts[0]}"
            } else {
                null
            }
        } else {
            // Формат: adapterId.deviceId.stateName (для tuya)
            val withoutAdapter = stateId.substring(adapterId.length + 1)
            val parts = withoutAdapter.split(".")
            
            if (parts.size > 1) {
                "${adapterId}.${parts[0]}"
            } else {
                null
            }
        }
    }
    
    /**
     * Извлечение имени устройства из ID
     * mihome.0.devices.gateway_286c07f0aa08 -> Gateway 286c07f0aa08
     * tuya.0.bf2a0780f7abcde10flvxn -> Device bf2a07
     */
    private fun extractDeviceName(deviceBaseId: String, adapterId: String): String {
        return try {
            val parts = deviceBaseId.split(".")
            val devicesIndex = parts.indexOf("devices")
            
            if (devicesIndex >= 0 && devicesIndex < parts.size - 1) {
                // Формат mihome: mihome.0.devices.deviceType_id
                val rawName = parts[devicesIndex + 1]
                val deviceType = rawName.substringBefore("_")
                val deviceId = rawName.substringAfter("_", "")
                
                val formattedType = deviceType.replaceFirstChar { it.uppercase() }
                val shortId = if (deviceId.length > 6) deviceId.take(6) else deviceId
                
                "$formattedType $shortId"
            } else {
                // Формат tuya: tuya.0.deviceId
                val deviceId = parts.lastOrNull { it != adapterId.split(".").last() } ?: deviceBaseId
                val shortId = if (deviceId.length > 6) deviceId.take(6) else deviceId
                "Device $shortId"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract device name from: $deviceBaseId", e)
            deviceBaseId
        }
    }
    
    /**
     * Извлекает имя устройства из объекта
     */
    private fun extractDeviceNameFromObject(deviceBaseId: String, deviceObj: JsonObject?, adapterId: String): String {
        // Пробуем получить из common.name объекта
        val common = deviceObj?.get("common") as? JsonObject
        val name = getJsonString(common, "name")
        if (!name.isNullOrBlank()) return name
        
        // Fallback: извлекаем из ID
        return extractDeviceName(deviceBaseId, adapterId)
    }
    
    /**
     * Определяет тип устройства по имени
     */
    private fun mapDeviceType(name: String): com.besenok.smarthome.domain.model.Device.DeviceType {
        val nameLower = name.lowercase()
        return when {
            nameLower.contains("gateway") -> com.besenok.smarthome.domain.model.Device.DeviceType.LIGHT
            nameLower.contains("sensor_ht") || nameLower.contains("motion") || 
            nameLower.contains("magnet") || nameLower.contains("weather") -> com.besenok.smarthome.domain.model.Device.DeviceType.SENSOR
            nameLower.contains("light") || nameLower.contains("lamp") || nameLower.contains("свет") -> com.besenok.smarthome.domain.model.Device.DeviceType.LIGHT
            nameLower.contains("socket") || nameLower.contains("outlet") || nameLower.contains("розетк") -> com.besenok.smarthome.domain.model.Device.DeviceType.SOCKET
            nameLower.contains("switch") || nameLower.contains("переключ") -> com.besenok.smarthome.domain.model.Device.DeviceType.SOCKET
            nameLower.contains("curtain") || nameLower.contains("blind") || nameLower.contains("штор") -> com.besenok.smarthome.domain.model.Device.DeviceType.BLIND
            nameLower.contains("thermostat") || nameLower.contains("climate") || nameLower.contains("термо") -> com.besenok.smarthome.domain.model.Device.DeviceType.THERMOSTAT
            else -> com.besenok.smarthome.domain.model.Device.DeviceType.OTHER
        }
    }
    
    /**
     * Определяет тип управления по role
     */
    private fun mapControlType(role: String): com.besenok.smarthome.domain.model.ControlType {
        val roleLower = role.lowercase()
        return when {
            roleLower.contains("illumination") || roleLower.contains("lux") || roleLower.contains("illuminance") -> com.besenok.smarthome.domain.model.ControlType.NONE
            roleLower.contains("switch") || roleLower.contains("light.on") -> com.besenok.smarthome.domain.model.ControlType.SWITCH
            roleLower.contains("dimmer") || roleLower.contains("level") -> com.besenok.smarthome.domain.model.ControlType.SLIDER
            roleLower.contains("color") || roleLower.contains("rgb") -> com.besenok.smarthome.domain.model.ControlType.COLOR_PICKER
            roleLower.contains("button") -> com.besenok.smarthome.domain.model.ControlType.NONE
            else -> com.besenok.smarthome.domain.model.ControlType.SWITCH
        }
    }
    
    private fun getJsonString(obj: JsonObject?, key: String): String? {
        return obj?.get(key)?.toString()?.trim('"')
    }
    
    private fun parseJsonValue(str: String?): Any? {
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

    suspend fun enumList(): List<String> {
        return emptyList()
    }

    suspend fun discoverAllDevices(): List<JsonObject> {
        return getObjects("*")?.filterValues { it is JsonObject }?.map { (id, obj) ->
            val jsonObject = obj as JsonObject
            val type = getJsonString(jsonObject, "type")
            if (type == "device") jsonObject else null
        }?.filterNotNull() ?: emptyList()
    }
}
