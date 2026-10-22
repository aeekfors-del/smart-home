package com.besenok.smarthome.data.repository

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.data.remote.iobroker.IobrokerApiClient
import com.besenok.smarthome.data.remote.iobroker.IobrokerRestClient
import com.besenok.smarthome.domain.model.AdapterInfo
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.Sensor
import com.besenok.smarthome.domain.model.Scenario
import com.besenok.smarthome.domain.model.State
import com.besenok.smarthome.domain.repository.ConnectionState
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.ui.common.UiState
import com.besenok.smarthome.util.JsonUtils
import com.besenok.smarthome.util.TypeMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IobrokerRepositoryImpl"

@Singleton
class IobrokerRepositoryImpl @Inject constructor(
    private val apiClient: IobrokerApiClient,
    private val restClient: IobrokerRestClient,
    private val settingsDataSource: AppPreferencesDataSource
) : IobrokerRepository {

    override val connectionState: StateFlow<ConnectionState> = restClient.connectionState

    override suspend fun connect() {
        restClient.connect()
    }

    override suspend fun disconnect() {
        restClient.disconnect()
    }

    override suspend fun getDevices(): UiState<List<Device>> {
        return try {
            Log.d(TAG, "Getting devices from ioBroker...")
            val devices = parseDevicesFromObjects()
            Log.d(TAG, "Parsed ${devices.size} devices: $devices")
            UiState.Success(applyCustomNames(devices))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices", e)
            UiState.Error(e.message ?: "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ СѓСЃС‚СЂРѕР№СЃС‚РІ")
        }
    }

    /**
     * Применяет пользовательские имена устройств и параметров (каналов) из DataStore
     */
    private suspend fun applyCustomNames(devices: List<Device>): List<Device> {
        if (devices.isEmpty()) return devices

        val deviceNames = settingsDataSource.getAllDeviceNames()
        val parameterNames = settingsDataSource.getAllParameterNames()
        if (deviceNames.isEmpty() && parameterNames.isEmpty()) return devices

        return devices.map { device ->
            device.copy(
                name = deviceNames[device.id]?.takeIf { it.isNotBlank() } ?: device.name,
                channels = if (parameterNames.isEmpty()) {
                    device.channels
                } else {
                    device.channels.map { channel ->
                        parameterNames[channel.id]?.takeIf { it.isNotBlank() }
                            ?.let { channel.copy(name = it) }
                            ?: channel
                    }
                }
            )
        }
    }

    private suspend fun parseDevicesFromObjects(): List<Device> {
        // РџРѕР»СѓС‡Р°РµРј СЃРїРёСЃРѕРє РґРѕР±Р°РІР»РµРЅРЅС‹С… Р°РґР°РїС‚РµСЂРѕРІ РёР· DataStore
        val addedAdapters = settingsDataSource.getAddedAdapterIds()
        Log.d(TAG, "Loaded ${addedAdapters.size} added adapters from DataStore: $addedAdapters")
        
        // РџРѕР»СѓС‡Р°РµРј СЃРїРёСЃРѕРє РґРѕР±Р°РІР»РµРЅРЅС‹С… deviceIds
        val addedDeviceIds = settingsDataSource.getAddedDeviceIds().toSet()
        Log.d(TAG, "Loaded ${addedDeviceIds.size} added deviceIds from DataStore: $addedDeviceIds")
        
        if (addedAdapters.isEmpty() && addedDeviceIds.isEmpty()) {
            Log.d(TAG, "No adapters or devices added yet, returning empty device list")
            return emptyList()
        }

        // Адаптеры загружаем параллельно: запросы независимы друг от друга
        val devicesByAdapter = coroutineScope {
            addedAdapters.map { adapterId ->
                async {
                    try {
                        val adapterDevices = apiClient.getAdapterDevices(adapterId)
                        Log.d(TAG, "Loaded ${adapterDevices.size} devices from adapter: $adapterId")
                        if (addedDeviceIds.isNotEmpty()) {
                            val filtered = adapterDevices.filter { it.id in addedDeviceIds }
                            Log.d(TAG, "Filtered ${filtered.size} devices (from ${adapterDevices.size}) based on addedDeviceIds")
                            filtered
                        } else {
                            adapterDevices
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading devices from adapter $adapterId", e)
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        val allDevices = devicesByAdapter.flatten()
        Log.d(TAG, "Total parsed devices from all adapters: ${allDevices.size}")
        return allDevices
    }
    
    /**
     * РџР°СЂСЃРёС‚ СѓСЃС‚СЂРѕР№СЃС‚РІР° РёР· РіСЂСѓРїРїС‹ (mihome.0, tuya.0, Рё С‚.Рґ.)
     */
    private suspend fun parseGroupDevices(
        pattern: String,
        groupName: String
    ): List<Device> {
        // РџРѕР»СѓС‡Р°РµРј РІСЃРµ РѕР±СЉРµРєС‚С‹ РёР· РіСЂСѓРїРїС‹
        val allObjectsResponse = apiClient.getObjects(pattern)
        val allObjects = allObjectsResponse?.filterValues { it is JsonObject }?.map { (id, obj) -> obj as JsonObject } ?: emptyList()
        Log.d(TAG, "Got ${allObjects.size} objects from $pattern")
        
        // Р“СЂСѓРїРїРёСЂСѓРµРј devices РїРѕ Р±Р°Р·РѕРІРѕРјСѓ ID СѓСЃС‚СЂРѕР№СЃС‚РІР°
        val devicesMap = mutableMapOf<String, MutableList<JsonObject>>()
        
        for (obj in allObjects) {
            val deviceId = JsonUtils.getString(obj, "_id") ?: continue
            
            // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ СЌС‚Рѕ state РёР· РЅСѓР¶РЅРѕР№ РіСЂСѓРїРїС‹
            if (!deviceId.startsWith("$pattern".removeSuffix(".*"))) continue
            
            // РР·РІР»РµРєР°РµРј Р±Р°Р·РѕРІС‹Р№ ID СѓСЃС‚СЂРѕР№СЃС‚РІР°
            val baseDeviceId = extractBaseDeviceId(deviceId)
            if (baseDeviceId == null) {
                Log.w(TAG, "Could not extract base device ID from: $deviceId")
                continue
            }
            
            devicesMap.getOrPut(baseDeviceId) { mutableListOf() }.add(obj)
        }
        
        Log.d(TAG, "Found ${devicesMap.size} unique devices in $groupName")
        
        // РџРѕР»СѓС‡Р°РµРј РІСЃРµ СЃРѕСЃС‚РѕСЏРЅРёСЏ РґР»СЏ РїРѕР»СѓС‡РµРЅРёСЏ Р·РЅР°С‡РµРЅРёР№
        val allStates = apiClient.getStates(pattern)
        Log.d(TAG, "Got ${allStates?.keys?.size} states from $pattern")
        
        // РџР°СЂСЃРёРј СѓСЃС‚СЂРѕР№СЃС‚РІР°
        val devices = mutableListOf<Device>()
        for ((baseId, stateObjects) in devicesMap) {
            val device = parseGroupDevice(baseId, stateObjects, allStates ?: emptyMap(), groupName)
            if (device != null) {
                devices.add(device)
                Log.d(TAG, "Parsed device: ${device.name} (${device.type}) with ${device.channels.size} channels")
            }
        }
        
        return devices
    }
    
    /**
     * РР·РІР»РµРєР°РµС‚ Р±Р°Р·РѕРІС‹Р№ ID СѓСЃС‚СЂРѕР№СЃС‚РІР° РёР· РїРѕР»РЅРѕРіРѕ ID state
     */
    private fun extractBaseDeviceId(fullId: String): String? {
        val parts = fullId.split(".")
        
        // Р”Р»СЏ Tuya: tuya.0.deviceId.stateName (РјРёРЅРёРјСѓРј 4 С‡Р°СЃС‚Рё)
        if (fullId.startsWith("tuya.0.")) {
            if (parts.size < 4) return null
            // Р‘РµСЂС‘Рј РїРµСЂРІС‹Рµ 3 С‡Р°СЃС‚Рё: tuya.0.deviceId
            return parts.take(3).joinToString(".")
        }
        
        // Р”Р»СЏ mihome: mihome.0.devices.deviceType_id.stateName (РјРёРЅРёРјСѓРј 5 С‡Р°СЃС‚РµР№)
        if (fullId.startsWith("mihome.0.devices.")) {
            if (parts.size < 5) return null
            // Р‘РµСЂС‘Рј РїРµСЂРІС‹Рµ 4 С‡Р°СЃС‚Рё: mihome.0.devices.deviceId
            return parts.take(4).joinToString(".")
        }
        
        return null
    }
    
    /**
     * РџР°СЂСЃРёС‚ СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РёР· РіСЂСѓРїРїС‹ (mihome.0, tuya.0, Рё С‚.Рґ.)
     */
    private fun parseGroupDevice(
        baseId: String,
        stateObjects: List<JsonObject>,
        allStates: Map<String, Any>,
        groupName: String
    ): Device? {
        if (stateObjects.isEmpty()) return null
        
        // РСЃРїРѕР»СЊР·СѓРµРј С‡РёС‚Р°РµРјРѕРµ РёРјСЏ РёР· baseId
        val deviceName = extractDeviceName(baseId, groupName)
        
        // РћРїСЂРµРґРµР»СЏРµРј С‚РёРї СѓСЃС‚СЂРѕР№СЃС‚РІР° РїРѕ ID
        val deviceType = mapDeviceType(baseId, groupName)
        
        // РџР°СЂСЃРёРј СЃРѕСЃС‚РѕСЏРЅРёСЏ СѓСЃС‚СЂРѕР№СЃС‚РІР° РєР°Рє channels
        val channels = mutableListOf<Device.Channel>()
        for (stateObj in stateObjects) {
            val stateId = JsonUtils.getString(stateObj, "_id") ?: continue
            
            // РС‰РµРј СЌС‚Рѕ СЃРѕСЃС‚РѕСЏРЅРёРµ РІ states РґР»СЏ РїРѕР»СѓС‡РµРЅРёСЏ Р·РЅР°С‡РµРЅРёСЏ
            val stateData = allStates[stateId] as? JsonObject
            
            val stateCommon = stateObj["common"] as? JsonObject ?: continue
            val stateName = JsonUtils.getString(stateCommon, "name") ?: JsonUtils.getString(stateCommon, "role") ?: ""
            val stateRole = JsonUtils.getString(stateCommon, "role") ?: ""
            val stateType = JsonUtils.getString(stateCommon, "type") ?: ""
            
            val controlType = mapControlType(stateRole, stateType, stateName)
            
            // РџРѕР»СѓС‡Р°РµРј С‚РµРєСѓС‰РµРµ Р·РЅР°С‡РµРЅРёРµ РёР· stateData (val, ack, ts)
            val value = stateData?.get("val")?.toString()?.let { JsonUtils.parseValue(it) }
            val ack = stateData?.get("ack")?.toString()?.toBoolean() ?: false
            val ts = stateData?.get("ts")?.toString()?.toLongOrNull() ?: 0
            val unit = JsonUtils.getString(stateCommon, "unit") ?: ""
            
            Log.d(TAG, "Channel $stateId: name=$stateName, value=$value, unit=$unit")
            
            // РџСЂРѕРІРµСЂСЏРµРј controlType РїРѕ РёРјРµРЅРё Рё ID РєР°РЅР°Р»Р°
            val channelControlType = if (stateId.contains("rgb", ignoreCase = true)) {
                ControlType.COLOR_PICKER
            } else {
                mapControlType(stateRole, stateType, stateName)
            }
            
            channels.add(
                Device.Channel(
                    id = stateId,
                    name = if (stateName.isNotEmpty()) stateName else stateId.substringAfterLast("."),
                    controlType = channelControlType,
                    state = State(
                        value = value,
                        ack = ack,
                        timestamp = ts,
                        unit = unit
                    )
                )
            )
        }
        
        // РџРѕР»СѓС‡Р°РµРј С‚РµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ СѓСЃС‚СЂРѕР№СЃС‚РІР° РёР· РїРµСЂРІРѕРіРѕ channel СЃ Р·РЅР°С‡РµРЅРёРµРј
        val deviceState = channels.firstOrNull { it.state != null && it.state!!.value != null }?.state
        
        return Device(
            id = baseId,
            name = deviceName,
            type = deviceType,
            controlType = when (deviceType) {
                Device.DeviceType.LIGHT -> ControlType.SWITCH
                Device.DeviceType.SENSOR -> ControlType.NONE
                else -> ControlType.NONE
            },
            state = deviceState,
            channels = channels,
            unit = null,
            icon = null,
            connectionGroup = groupName
        )
    }
    
    /**
     * РР·РІР»РµРєР°РµС‚ С‡РёС‚Р°РµРјРѕРµ РёРјСЏ СѓСЃС‚СЂРѕР№СЃС‚РІР° РёР· baseId
     */
    private fun extractDeviceName(baseId: String, groupName: String): String {
        // РЈР±РёСЂР°РµРј РїСЂРµС„РёРєСЃ РіСЂСѓРїРїС‹ (mihome.0.devices. РёР»Рё tuya.0.)
        val deviceId = when (groupName) {
            "mihome.0" -> baseId.substringAfter("mihome.0.devices.")
            "tuya.0" -> baseId.substringAfter("tuya.0.")
            else -> baseId.substringAfterLast(".")
        }
        
        // РћРїСЂРµРґРµР»СЏРµРј С‚РёРї СѓСЃС‚СЂРѕР№СЃС‚РІР° РїРѕ ID
        val deviceType = when (groupName) {
            "mihome.0" -> mapMiHomeDeviceTypeName(deviceId)
            "tuya.0" -> mapTuyaDeviceTypeName(deviceId)
            else -> deviceId
        }
        
        // Р”Р»СЏ Tuya: РёСЃРїРѕР»СЊР·СѓРµРј С‚РёРї СѓСЃС‚СЂРѕР№СЃС‚РІР° + РїРµСЂРІС‹Рµ 6 СЃРёРјРІРѕР»РѕРІ ID
        if (groupName == "tuya.0") {
            // РЈР±РёСЂР°РµРј СЃСѓС„С„РёРєСЃС‹ С‚РёРїР° .1, .2, -0, -1 Рё С‚.Рґ.
            val cleanDeviceId = deviceId.split(".")[0].split("-")[0]
            val shortId = if (cleanDeviceId.length > 6) cleanDeviceId.take(6) else cleanDeviceId
            // Р•СЃР»Рё deviceType РЅРµ РѕРїСЂРµРґРµР»С‘РЅ (fallback), РёСЃРїРѕР»СЊР·СѓРµРј "РЈСЃС‚СЂРѕР№СЃС‚РІРѕ"
            val typeName = if (deviceType == deviceId) "РЈСЃС‚СЂРѕР№СЃС‚РІРѕ" else deviceType
            return "$typeName $shortId"
        }
        
        // Р”Р»СЏ mihome: РёР·РІР»РµРєР°РµРј ID (РїРѕСЃР»РµРґРЅРёРµ СЃРёРјРІРѕР»С‹ РїРѕСЃР»Рµ _)
        val idPart = deviceId.substringAfter("_")
        val shortId = if (idPart.isNotEmpty() && idPart != deviceId) idPart else deviceId.takeLast(6)
        return "$deviceType $shortId"
    }
    
    private fun mapMiHomeDeviceTypeName(deviceId: String): String {
        return when {
            deviceId.startsWith("gateway") -> "Gateway"
            deviceId.startsWith("sensor_ht") -> "РўРµРјРїРµСЂР°С‚СѓСЂР°"
            deviceId.startsWith("motion") -> "Р”Р°С‚С‡РёРє РґРІРёР¶РµРЅРёСЏ"
            deviceId.startsWith("magnet") -> "Р”Р°С‚С‡РёРє РѕС‚РєСЂС‹С‚РёСЏ"
            deviceId.startsWith("weather") -> "РџРѕРіРѕРґР°"
            deviceId.startsWith("switch") -> "РљРЅРѕРїРєР°"
            else -> deviceId
        }
    }
    
    private fun mapTuyaDeviceTypeName(deviceId: String): String {
        return when {
            deviceId.contains("light") || deviceId.contains("lamp") -> "РЎРІРµС‚"
            deviceId.contains("socket") || deviceId.contains("outlet") -> "Р РѕР·РµС‚РєР°"
            deviceId.contains("switch") -> "РџРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ"
            deviceId.contains("sensor") -> "Р”Р°С‚С‡РёРє"
            deviceId.contains("thermostat") || deviceId.contains("temp") -> "РўРµСЂРјРѕСЃС‚Р°С‚"
            deviceId.contains("curtain") || deviceId.contains("blind") -> "РЁС‚РѕСЂР°"
            deviceId.contains("camera") -> "РљР°РјРµСЂР°"
            deviceId.contains("fan") -> "Р’РµРЅС‚РёР»СЏС‚РѕСЂ"
            else -> deviceId
        }
    }
    
    /**
     * РћРїСЂРµРґРµР»СЏРµС‚ С‚РёРї СѓСЃС‚СЂРѕР№СЃС‚РІР°
     */
    private fun mapDeviceType(baseId: String, groupName: String): Device.DeviceType {
        return when (groupName) {
            "mihome.0" -> mapMiHomeDeviceType(baseId)
            "tuya.0" -> mapTuyaDeviceType(baseId)
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapMiHomeDeviceType(baseId: String): Device.DeviceType {
        return when {
            baseId.contains("gateway") -> Device.DeviceType.LIGHT
            baseId.contains("sensor_ht") -> Device.DeviceType.SENSOR
            baseId.contains("motion") -> Device.DeviceType.SENSOR
            baseId.contains("magnet") -> Device.DeviceType.SENSOR
            baseId.contains("weather_v1") || baseId.contains("weather") -> Device.DeviceType.SENSOR
            baseId.contains("switch") -> Device.DeviceType.OTHER
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapTuyaDeviceType(baseId: String): Device.DeviceType {
        return when {
            baseId.contains("light") || baseId.contains("lamp") -> Device.DeviceType.LIGHT
            baseId.contains("socket") || baseId.contains("outlet") -> Device.DeviceType.SOCKET
            baseId.contains("switch") -> Device.DeviceType.SOCKET
            baseId.contains("sensor") -> Device.DeviceType.SENSOR
            baseId.contains("thermostat") || baseId.contains("temp") -> Device.DeviceType.THERMOSTAT
            baseId.contains("curtain") || baseId.contains("blind") -> Device.DeviceType.BLIND
            baseId.contains("fan") -> Device.DeviceType.OTHER
            else -> Device.DeviceType.OTHER
        }
    }
    
    /**
     * РћРїСЂРµРґРµР»СЏРµС‚ С‚РёРї СѓРїСЂР°РІР»РµРЅРёСЏ
     */
    private fun mapControlType(role: String, type: String, name: String? = null): ControlType {
        val roleLower = role.lowercase()
        val nameLower = name?.lowercase() ?: ""
        return when {
            roleLower.contains("illumination") || roleLower.contains("lux") || roleLower.contains("illuminance") -> ControlType.NONE
            roleLower.contains("switch") || roleLower.contains("light.on") -> ControlType.SWITCH
            roleLower.contains("dimmer") || roleLower.contains("level") -> ControlType.SLIDER
            roleLower.contains("color") || roleLower.contains("rgb") || nameLower.contains("rgb") -> ControlType.COLOR_PICKER
            roleLower.contains("button") -> ControlType.NONE
            else -> ControlType.SWITCH
        }
    }

    private suspend fun parseDevice(obj: JsonObject): Device? {
        val common = obj["common"] as? JsonObject ?: return null
        val type = obj["type"]?.toString() ?: return null
        
        // Only parse devices and channels
        if (type != "device" && type != "channel") return null
        
        val deviceId = obj["_id"]?.toString() ?: return null
        val deviceName = JsonUtils.getString(common, "name") ?: deviceId
        
        // Determine device type from ioBroker type
        val deviceType = mapToDeviceType(common)
        val controlType = mapToControlType(common)
        
        // Find state
        var deviceState: State? = null
        var icon = JsonUtils.getString(common, "icon")
        
        // Try to get state from members
        val membersStr = common["members"]?.toString()
        if (!membersStr.isNullOrEmpty()) {
            try {
                val memberId = membersStr.trim('[', ']', ' ', '"')
                if (memberId.isNotEmpty()) {
                    deviceState = getStateSync(memberId)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // If no state from members, try to find state child object
        if (deviceState == null) {
            val stateObj = findStateChildSync(deviceId)
            if (stateObj != null) {
                deviceState = parseStateObject(stateObj)
                icon = JsonUtils.getString(stateObj["common"] as? JsonObject, "icon") ?: icon
            }
        }
        
        // Parse channels if this is a device
        val channels = if (type == "device") {
            parseChannelsSync(deviceId)
        } else {
            emptyList()
        }
        
        val unit = JsonUtils.getString(common, "unit")
        val unitInState = deviceState?.unit ?: unit
        
        return Device(
            id = deviceId,
            name = deviceName,
            type = deviceType,
            controlType = controlType,
            state = deviceState?.copy(unit = unitInState),
            channels = channels,
            unit = unit,
            icon = icon
        )
    }
    
    private suspend fun parseChannelsSync(deviceId: String): List<Device.Channel> {
        val channels = mutableListOf<Device.Channel>()
        
        try {
            val channelPattern = "$deviceId.*"
            val channelObjectsResponse = apiClient.getObjects(channelPattern)
            val channelObjects = channelObjectsResponse?.filterValues { it is JsonObject }?.map { (id, obj) -> obj as JsonObject } ?: emptyList()
            
            for (channelObj in channelObjects) {
                val channel = parseChannelSync(channelObj)
                if (channel != null) {
                    channels.add(channel)
                }
            }
        } catch (e: Exception) {
            // Ignore channel parsing errors
        }
        
        return channels
    }
    
    private suspend fun parseChannelSync(obj: JsonObject): Device.Channel? {
        val common = obj["common"] as? JsonObject ?: return null
        val type = obj["type"]?.toString() ?: return null
        
        if (type != "channel") return null
        
        val channelId = obj["_id"]?.toString() ?: return null
        val channelName = JsonUtils.getString(common, "name") ?: channelId
        
        val controlType = mapToControlType(common)
        
        // Try to find state
        var channelState: State? = null
        val membersStr = common["members"]?.toString()
        if (!membersStr.isNullOrEmpty()) {
            try {
                val memberId = membersStr.trim('[', ']', ' ', '"')
                if (memberId.isNotEmpty()) {
                    channelState = getStateSync(memberId)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        if (channelState == null) {
            val stateObj = findStateChildSync(channelId)
            if (stateObj != null) {
                channelState = parseStateObject(stateObj)
            }
        }
        
        return Device.Channel(
            id = channelId,
            name = channelName,
            controlType = controlType,
            state = channelState
        )
    }
    
    private suspend fun findStateChildSync(deviceId: String): JsonObject? {
        try {
            val statePattern = "$deviceId.0" // Typical state ID pattern
            val stateObjectsResponse = apiClient.getObjects(statePattern)
            val stateObjects = stateObjectsResponse?.filterValues { it is JsonObject }?.map { (id, obj) -> obj as JsonObject } ?: emptyList()
            return stateObjects.firstOrNull()
        } catch (e: Exception) {
            return null
        }
    }
    
    private suspend fun getStateSync(objectId: String): State? {
        return try {
            val iobrokerState = apiClient.getState(objectId)
            if (iobrokerState != null) {
                State(
                    value = iobrokerState.value,
                    ack = iobrokerState.ack,
                    timestamp = iobrokerState.ts.toLong(),
                    unit = null
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseStateObject(obj: JsonObject): State {
        val common = obj["common"] as? JsonObject ?: JsonObject(emptyMap())
        
        val rawValue = obj["val"]
        val value = JsonUtils.parseValue(rawValue?.toString())
        
        val ack = JsonUtils.getString(obj, "ack")?.toBoolean() ?: false
        val ts = JsonUtils.getString(obj, "ts")?.toLongOrNull() ?: 0
        val unit = JsonUtils.getString(common, "unit")
        
        return State(
            value = value,
            ack = ack,
            timestamp = ts,
            unit = unit
        )
    }
    
    private fun mapToDeviceType(common: JsonObject): Device.DeviceType {
        val typeString = JsonUtils.getString(common, "type")?.lowercase()
        val customType = common["custom"]?.let { custom ->
            custom as? JsonObject
        }?.get("0")?.let { customObj ->
            customObj as? JsonObject
        }?.let { customObj ->
            JsonUtils.getString(customObj, "type")
        }?.lowercase()
        
        return when (customType ?: typeString) {
            "light", "lightbulb", "lamp" -> Device.DeviceType.LIGHT
            "socket", "outlet", "receptacle" -> Device.DeviceType.SOCKET
            "thermostat", "climate", "heater" -> Device.DeviceType.THERMOSTAT
            "blind", "shutter", "curtain" -> Device.DeviceType.BLIND
            "gate", "door", "garage" -> Device.DeviceType.GATE
            "sensor", "weather", "meter" -> Device.DeviceType.SENSOR
            else -> Device.DeviceType.OTHER
        }
    }
    
    private fun mapToControlType(common: JsonObject): ControlType {
        val role = JsonUtils.getString(common, "role")?.lowercase() ?: ""
        val type = JsonUtils.getString(common, "type")?.lowercase() ?: ""
        
        return when {
            role.contains("dim.level") || role.contains("light.on") -> ControlType.SLIDER
            role.contains("blinds") || role.contains("shutter") -> ControlType.SLIDER
            type == "number" || type == "integer" -> ControlType.NUMBER_INPUT
            type == "string" || type == "text" -> ControlType.TEXT_INPUT
            type == "boolean" || role.contains("switch") || role.contains("power") -> ControlType.SWITCH
            type == "number" && role.contains("heat") -> ControlType.SLIDER
            else -> ControlType.SWITCH
        }
    }
    
    override suspend fun getDevice(deviceId: String): UiState<Device> {
        return try {
            Log.d(TAG, "Getting device $deviceId from mihome.0")
            
            // РџРѕР»СѓС‡Р°РµРј РІСЃРµ state РѕР±СЉРµРєС‚С‹ РґР»СЏ СЌС‚РѕРіРѕ СѓСЃС‚СЂРѕР№СЃС‚РІР°
            val pattern = "mihome.0.devices.*"
            val allStates = apiClient.getStates(pattern)
            
            val deviceStates = allStates?.entries?.filterNotNull()?.filter { (stateId, _) ->
                stateId.contains(deviceId.split(".").take(4).joinToString("."))
            }?.associate { (stateId, stateObj) ->
                stateId to (stateObj as? JsonObject)
            } ?: emptyMap()
            
            Log.d(TAG, "Found ${deviceStates.size} states for device $deviceId")
            
            // РЎРѕР·РґР°РµРј СѓСЃС‚СЂРѕР№СЃС‚РІРѕ СЃ Р·Р°РіСЂСѓР¶РµРЅРЅС‹РјРё СЃРѕСЃС‚РѕСЏРЅРёСЏРјРё
            val device = Device(
                id = deviceId,
                name = deviceId.split(".").lastOrNull() ?: deviceId,
                type = mapMiHomeDeviceType(deviceId),
                controlType = ControlType.SWITCH,
                state = null,
                channels = deviceStates.mapNotNull { (stateId, stateObj) ->
                    val common = stateObj?.get("common") as? JsonObject ?: return@mapNotNull null
                    val role = JsonUtils.getString(common, "role") ?: ""
                    
                    // РџСЂРѕРїСѓСЃРєР°РµРј battery
                    if (role.contains("battery")) return@mapNotNull null
                    
                    val value = stateObj["val"]?.toString()?.let { JsonUtils.parseValue(it) }
                    val unit = JsonUtils.getString(common, "unit") ?: ""
                    val name = JsonUtils.getString(common, "name") ?: stateId
                    val ts = stateObj["ts"]?.toString()?.toLongOrNull() ?: 0
                    
                    Device.Channel(
                        id = stateId,
                        name = name,
                        controlType = mapControlType(role, JsonUtils.getString(common, "type") ?: ""),
                        state = State(
                            value = value,
                            ack = stateObj["ack"]?.toString()?.toBoolean() ?: false,
                            timestamp = ts,
                            unit = unit
                        )
                    )
                },
                unit = null,
                icon = null
            )
            
            UiState.Success(applyCustomNames(listOf(device)).first())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device $deviceId", e)
            UiState.Error(e.message ?: "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ СѓСЃС‚СЂРѕР№СЃС‚РІР°")
        }
    }

    override suspend fun getState(objectId: String): UiState<Any?> {
        return try {
            val state = apiClient.getState(objectId)
            Log.d(TAG, "getState($objectId): state=$state, value=${state?.value}")
            if (state == null) {
                Log.w(TAG, "getState($objectId): state object is null")
                UiState.Error("Не удалось получить значение параметра")
            } else {
                UiState.Success(state.value)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Not connected while getting state $objectId", e)
            UiState.Error("Нет соединения с ioBroker")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting state $objectId", e)
            UiState.Error(e.message ?: "Ошибка получения значения параметра")
        }
    }

    override suspend fun setState(objectId: String, value: Any, ack: Boolean): UiState<Unit> {
        return try {
            Log.d(TAG, "Setting state $objectId = $value (ack=$ack)")
            // РСЃРїРѕР»СЊР·СѓРµРј /set/ endpoint РІРјРµСЃС‚Рѕ /json/set/
            apiClient.setState(objectId, value)
            Log.d(TAG, "State $objectId set successfully")
            UiState.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting state $objectId", e)
            UiState.Error(e.message ?: "Failed to set state")
        }
    }

    override suspend fun getAdapters(): UiState<List<AdapterInfo>> {
        return try {
            Log.d(TAG, "Getting adapters with alive status...")
            val adapters = apiClient.getAdapterList()
            Log.d(TAG, "Got ${adapters.size} active adapters: ${adapters.map { it.id }}")
            UiState.Success(adapters)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting adapters", e)
            UiState.Error(e.message ?: "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ Р°РґР°РїС‚РµСЂРѕРІ")
        }
    }

    override suspend fun getDevicesFromAdapter(adapterId: String): UiState<List<Device>> {
        return try {
            Log.d(TAG, "Getting devices from adapter: $adapterId...")
            val devices = apiClient.getAdapterDevices(adapterId)
            Log.d(TAG, "Got ${devices.size} devices from adapter $adapterId")
            UiState.Success(applyCustomNames(devices))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices from adapter $adapterId", e)
            UiState.Error(e.message ?: "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ СѓСЃС‚СЂРѕР№СЃС‚РІ РёР· Р°РґР°РїС‚РµСЂР°")
        }
    }

    override suspend fun getSensors(): UiState<List<Sensor>> {
        return try {
            // РџРѕР»СѓС‡Р°РµРј РІСЃРµ state РѕР±СЉРµРєС‚С‹ РёР· mihome.0
            val allStates = apiClient.getStates("mihome.0.devices.*")
            Log.d(TAG, "Got states from mihome.0: ${allStates?.keys?.size}")
            
            val sensors = mutableListOf<Sensor>()
            
            if (allStates != null) {
                for ((stateId, stateObj) in allStates) {
                    val stateData = stateObj as? JsonObject ?: continue
                    val common = stateData["common"] as? JsonObject ?: continue
                    
                    val role = JsonUtils.getString(common, "role") ?: ""
                    val sensorType = mapMiHomeSensorType(role)
                    
                    // РџСЂРѕРїСѓСЃРєР°РµРј battery Рё non-sensor states
                    if (sensorType == null || role.contains("battery")) continue
                    
                    // РџРѕР»СѓС‡Р°РµРј С‚РµРєСѓС‰РµРµ Р·РЅР°С‡РµРЅРёРµ
                    val value = stateData["val"]?.toString()?.let { JsonUtils.parseValue(it) }
                    val unit = JsonUtils.getString(common, "unit") ?: ""
                    val name = JsonUtils.getString(common, "name") ?: stateId
                    val ts = stateData["ts"]?.toString()?.toLongOrNull() ?: 0
                    
                    sensors.add(
                        Sensor(
                            id = stateId,
                            name = name,
                            sensorType = sensorType,
                            value = value,
                            unit = unit,
                            timestamp = ts
                        )
                    )
                }
            }
            
            Log.d(TAG, "Parsed ${sensors.size} sensors from mihome.0")
            UiState.Success(sensors)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sensors", e)
            UiState.Error(e.message ?: "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ РґР°С‚С‡РёРєРѕРІ")
        }
    }
    
    /**
     * РћРїСЂРµРґРµР»СЏРµС‚ С‚РёРї РґР°С‚С‡РёРєР° mihome РїРѕ role
     */
    private fun mapMiHomeSensorType(role: String): Sensor.SensorType? {
        val roleLower = role.lowercase()
        
        return when {
            roleLower.contains("temperature") -> Sensor.SensorType.TEMPERATURE
            roleLower.contains("humidity") -> Sensor.SensorType.HUMIDITY
            roleLower.contains("pressure") -> Sensor.SensorType.PRESSURE
            roleLower.contains("lux") || roleLower.contains("illuminance") -> Sensor.SensorType.LIGHT
            roleLower.contains("motion") || roleLower.contains("indicator.motion") -> Sensor.SensorType.MOTION
            roleLower.contains("magnet") || roleLower.contains("state") && roleLower.contains("opened") -> Sensor.SensorType.DOOR_WINDOW
            else -> null
        }
    }

    override suspend fun getScenarios(): UiState<List<Scenario>> {
        // Р’ mihome.0 РЅРµС‚ С‚СЂР°РґРёС†РёРѕРЅРЅС‹С… СЃС†РµРЅР°СЂРёРµРІ
        // РњРѕР¶РЅРѕ РґРѕР±Р°РІРёС‚СЊ СЃС†РµРЅР°СЂРёРё РїРѕР·Р¶Рµ С‡РµСЂРµР· ioBroker adapter scenarios
        return UiState.Success(emptyList())
    }

    override suspend fun executeScenario(scenarioId: String): UiState<Unit> {
        return UiState.Success(Unit)
    }
}


