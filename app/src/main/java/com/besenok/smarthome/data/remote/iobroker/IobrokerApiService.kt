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
            
            // Автоматическая классификация на основе ролей каналов
            val deviceType = detectDeviceTypeFromChannels(channels, deviceName)
            
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
     * Автоматическая классификация устройства на основе ролей каналов
     * Аналог ioBroker.type-detector: анализирует все каналы устройства
     * 
     * Критерии:
     * - SENSOR: есть каналы с role=sensor (motion, magnet, illumination, temperature, etc.)
     * - LIGHT: есть каналы с role=switch.light или level/dimmer/color
     * - SOCKET: есть каналы с role=socket или switch.main
     * - THERMOSTAT: есть каналы с temperature + setpoint/hysteresis
     * - BLIND: есть каналы с role=blind или jalousie
     * - GATE: есть каналы с role=gate или garage
     */
    private fun detectDeviceTypeFromChannels(
        channels: List<com.besenok.smarthome.domain.model.Device.Channel>,
        fallbackName: String
    ): com.besenok.smarthome.domain.model.Device.DeviceType {
        if (channels.isEmpty()) {
            Log.w(TAG, "No channels for device $fallbackName, using fallback name-based detection")
            return mapDeviceType(fallbackName)
        }
        
        val channelRoles = channels.map { it.name.lowercase() }.toSet()
        
        var bestType: com.besenok.smarthome.domain.model.Device.DeviceType? = null
        var maxScore = 0
        
        // 5. SOCKET: socket/outlet/main switch (нужно объявить до использования)
        val hasSocket = channelRolesAny(channels) {
            it.contains("socket") || it.contains("outlet") || it.contains("розетк")
        }
        
        // 1. THERMOSTAT: temperature + setpoint/hysteresis/heating
        val hasTemperature = channelRolesAny(channels) { 
            it.contains("temperature") || it.contains("temp") || it.contains("температур") 
        }
        val hasSetpoint = channelRolesAny(channels) {
            it.contains("setpoint") || it.contains("hysteresis") || it.contains("target") || 
            it.contains("зона") || it.contains("задач")
        }
        val hasHeating = channelRolesAny(channels) {
            it.contains("heating") || it.contains("обогрев") || it.contains("нагрев")
        }
        
        if ((hasTemperature && hasSetpoint) || (hasTemperature && hasHeating)) {
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.THERMOSTAT
            maxScore = 10
        }
        
        // 2. BLIND: curtain/blind/jalousie/roll
        val hasBlind = channelRolesAny(channels) {
            it.contains("blind") || it.contains("curtain") || it.contains("jalousie") || 
            it.contains("roll") || it.contains("штор") || it.contains("жало") || it.contains("рулон")
        }
        val hasLevel = channelRolesAny(channels) {
            it.contains("level") || it.contains("brightness") || it.contains("ярк")
        }
        
        if (hasBlind) {
            if (maxScore < 9) {
                bestType = com.besenok.smarthome.domain.model.Device.DeviceType.BLIND
                maxScore = 9
            }
        } else if (hasLevel && channels.size <= 3) {
            // Level без color может быть blind
            if (maxScore < 5) {
                bestType = com.besenok.smarthome.domain.model.Device.DeviceType.BLIND
                maxScore = 5
            }
        }
        
        // 3. GATE: gate/garage/door
        val hasGate = channelRolesAny(channels) {
            it.contains("gate") || it.contains("garage") || it.contains("ворота") || 
            it.contains("двер") || it.contains("portal")
        }
        
        if (hasGate) {
            if (maxScore < 9) {
                bestType = com.besenok.smarthome.domain.model.Device.DeviceType.GATE
                maxScore = 9
            }
        }
        
        // 4. LIGHT: switch.light/dimmer/color
        val hasLightSwitch = channelRolesAny(channels) {
            it.contains("light.on") || it.contains("light_switch") || it.contains("свет")
        }
        val hasDimmer = channelRolesAny(channels) {
            it.contains("dimmer") || it.contains("dim") || it.contains("ярк")
        }
        val hasColor = channelRolesAny(channels) {
            it.contains("color") || it.contains("rgb") || it.contains("Цвет")
        }
        val hasSwitch = channelRolesAny(channels) {
            it.contains("switch") || it.contains("переключ")
        }
        
        if ((hasLightSwitch || hasDimmer || hasColor) && maxScore < 8) {
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.LIGHT
            maxScore = 8
        } else if (hasSwitch && !hasSocket && maxScore < 4) {
            // Простой switch без socket - может быть light
            if (hasLevel || channels.any { it.controlType == com.besenok.smarthome.domain.model.ControlType.SLIDER }) {
                bestType = com.besenok.smarthome.domain.model.Device.DeviceType.LIGHT
                maxScore = 4
            }
        }
        
        if (hasSocket && maxScore < 7) {
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.SOCKET
            maxScore = 7
        }
        
        // 6. SENSOR: sensor roles (motion, magnet, illumination, temperature, etc.)
        val hasMotion = channelRolesAny(channels) {
            it.contains("motion") || it.contains("movement") || it.contains("движ") || 
            it.contains("pir") || it.contains("присутств")
        }
        val hasMagnet = channelRolesAny(channels) {
            it.contains("magnet") || it.contains("contact") || it.contains("открыв") || 
            it.contains("закрыт") || it.contains("window") || it.contains("door_sensor")
        }
        val hasIllumination = channelRolesAny(channels) {
            it.contains("illumination") || it.contains("lux") || it.contains("освещ")
        }
        val hasHumidity = channelRolesAny(channels) {
            it.contains("humidity") || it.contains("влажн")
        }
        val hasPressure = channelRolesAny(channels) {
            it.contains("pressure") || it.contains("давлен")
        }
        val hasWater = channelRolesAny(channels) {
            it.contains("water") || it.contains("течь") || it.contains("поток")
        }
        val hasGas = channelRolesAny(channels) {
            it.contains("gas") || it.contains("газ")
        }
        val hasSmoke = channelRolesAny(channels) {
            it.contains("smoke") || it.contains("дым")
        }
        val hasButton = channelRolesAny(channels) {
            it.contains("button") || it.contains("кнопк") || it.contains("button_event")
        }
        
        val sensorKeywordsCount = listOf(
            hasMotion, hasMagnet, hasIllumination, hasHumidity, hasPressure,
            hasWater, hasGas, hasSmoke
        ).count { it }
        
        // 6b. Tuya-сенсоры: voltage, percent, temperature, doublePress, click, state
        val hasVoltage = channelRolesAny(channels) { it.contains("voltage") }
        val hasPercent = channelRolesAny(channels) { it.contains("percent") || it.contains("battery") }
        val hasDoublePress = channelRolesAny(channels) { it.contains("doublepress") || it.contains("double_press") || it.contains("double") }
        val hasClick = channelRolesAny(channels) { it.contains("click") || it.contains("button") }
        val hasState = channelRolesAny(channels) { it.contains(".state") || it.contains("state") }
        
        val tuyaSensorCount = listOf(hasVoltage, hasPercent, hasDoublePress, hasClick, hasState).count { it }
        
        if (sensorKeywordsCount > 0 && maxScore < sensorKeywordsCount + 2) {
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.SENSOR
            maxScore = sensorKeywordsCount + 2
        } else if (tuyaSensorCount > 0 && maxScore < tuyaSensorCount + 1) {
            // Tuya-устройства с voltage/percent/doublePress/click - это сенсоры
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.SENSOR
            maxScore = tuyaSensorCount + 1
            Log.d(TAG, "  🔌 Detected TUYA SENSOR by channel names: voltage=$hasVoltage percent=$hasPercent doublePress=$hasDoublePress click=$hasClick state=$hasState")
        }
        
        // 7. Tuya-устройства с числовыми каналами (DP IDs: 1, 9, 17, 18, 19...)
        // Если большинство каналов - числа, это скорее всего розетка/переключатель
        val numericNames = channels.count { it.name.matches(Regex("^\\d+$")) }
        val numericNamesWithDash = channels.count { it.name.matches(Regex("^\\d+-\\d+$")) }
        val totalNumericNames = numericNames + numericNamesWithDash
        
        if (totalNumericNames > channels.size / 2 && channels.size > 5) {
            // Больше 50% каналов - числа, это Tuya устройство
            // Tuya с множеством каналов - это умная розетка/переключатель/свет
            if (maxScore < 6) {
                bestType = com.besenok.smarthome.domain.model.Device.DeviceType.SOCKET
                maxScore = 6
                Log.d(TAG, "  🔌 Detected TUYA SOCKET by numeric DP IDs: $totalNumericNames numeric channels out of ${channels.size}")
            }
        }
        
        // 8. Устройства с числовыми каналами (MQTT водосчётчик и т.п.)
        // Если есть v (напряжение), i (ток), w (мощность), wh (энергия), Cold/Hot (счётчики)
        val hasCurrent = channelRolesAny(channels) { it.contains("current") || it.contains(".i") || it.contains("ток") }
        val hasPower = channelRolesAny(channels) { it.contains("power") || it.contains(".w") || it.contains("мощн") }
        val hasEnergy = channelRolesAny(channels) { it.contains("energy") || it.contains(".wh") || it.contains("энерги") }
        val hasMeter = channelRolesAny(channels) { it.contains("meter") || it.contains("счётчик") || it.contains("metric") }
        val hasCold = channelRolesAny(channels) { it.contains("cold") || it.contains("hot_water") || it.contains("холод") }
        val hasHot = channelRolesAny(channels) { it.contains("hot") && !it.contains("heating") && !it.contains("hot_water") || it.contains("горяч") }
        
        val numericChannelCount = listOf(hasCurrent, hasPower, hasEnergy, hasMeter, hasCold, hasHot).count { it }
        
        if (numericChannelCount > 2 && maxScore < numericChannelCount + 1) {
            // Устройство с множеством числовых каналов - это SENSOR (счётчики, метрики)
            bestType = com.besenok.smarthome.domain.model.Device.DeviceType.SENSOR
            maxScore = numericChannelCount + 1
            Log.d(TAG, "  📊 Detected SENSOR by numeric channels: i=$hasCurrent w=$hasPower wh=$hasEnergy meter=$hasMeter")
        }
        
        // Fallback: используем определение по имени
        val finalType = bestType ?: mapDeviceType(fallbackName)
        Log.d(TAG, "  🎯 Final device type for '$fallbackName': $finalType (score=$maxScore, channels=${channels.size})")
        return finalType
    }
    
    private fun channelRolesAny(channelsList: List<com.besenok.smarthome.domain.model.Device.Channel>, predicate: (String) -> Boolean): Boolean {
        return channelsList.any { predicate(it.name.lowercase()) }
    }
    
    /**
     * Определяет тип устройства по имени (fallback)
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
