package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.domain.model.State
import com.besenok.smarthome.util.JsonUtils
import com.besenok.smarthome.util.TypeMapper
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceParser"

/**
 * Парсер устройств из JSON объектов ioBroker
 * Ответственность: извлечение device/channel/state из JSON
 */
@Singleton
class DeviceParser @Inject constructor(
    private val restClient: IobrokerRestClient
) {

    /**
     * Парсит устройства из адаптера
     */
    suspend fun parseAdapterDevices(
        adapterId: String,
        states: JsonObject,
        objects: JsonObject?,
        classifier: DeviceClassifier
    ): List<Device> {
        Log.d(TAG, "========== PARSE ADAPTER DEVICES ==========")
        Log.d(TAG, "adapterId: $adapterId")
        Log.d(TAG, "Total states: ${states.size}")

        // Группируем states по устройствам
        val deviceGroups = mutableMapOf<String, MutableList<Pair<String, JsonObject>>>()

        for ((stateId, stateValue) in states) {
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
            val deviceObj = objects?.get(deviceBaseId) as? JsonObject
            val deviceName = extractDeviceNameFromObject(deviceBaseId, deviceObj, adapterId)

            // Создаём каналы из состояний
            val channels = stateList.map { (stateId, stateObj) ->
                parseChannelFromState(stateId, stateObj)
            }

            // Автоматическая классификация на основе ролей каналов
            val deviceType = classifier.classify(channels, deviceName)

            Log.d(TAG, "  ✓ Device: $deviceName (type=${deviceType.name}, channels=${channels.size})")

            Device(
                id = deviceBaseId,
                name = deviceName,
                type = deviceType,
                controlType = when (deviceType) {
                    Device.DeviceType.LIGHT -> ControlType.SWITCH
                    else -> ControlType.NONE
                },
                state = channels.firstOrNull { it.state?.value != null }?.state,
                channels = channels,
                connectionGroup = adapterId
            )
        }

        Log.d(TAG, "========== END PARSE ADAPTER DEVICES: ${devices.size} devices ==========")
        return devices
    }

    /**
     * Парсит channel из state объекта
     */
    private fun parseChannelFromState(stateId: String, stateObj: JsonObject): Device.Channel {
        val common = stateObj["common"] as? JsonObject ?: JsonObject(emptyMap())
        val stateName = JsonUtils.getString(common, "name") ?: JsonUtils.getString(common, "role") ?: stateId.substringAfterLast(".")
        val stateRole = JsonUtils.getString(common, "role") ?: ""

        // Получаем текущее значение
        val value = stateObj["val"]?.toString()?.let { JsonUtils.parseValue(it) }
        val ack = stateObj["ack"]?.toString()?.toBoolean() ?: false
        val ts = stateObj["ts"]?.toString()?.toLongOrNull() ?: 0
        val unit = JsonUtils.getString(common, "unit")

        Log.d(TAG, "    Channel: $stateName (role=$stateRole) = $value $unit")

        return Device.Channel(
            id = stateId,
            name = stateName,
            controlType = TypeMapper.mapControlTypeFromRole(stateRole),
            state = State(
                value = value,
                ack = ack,
                timestamp = ts,
                unit = unit
            )
        )
    }

    /**
     * Извлечение базового ID устройства из state ID
     * mihome.0.devices.gateway_286c07f0aa08.illumination -> mihome.0.devices.gateway_286c07f0aa08
     * tuya.0.bf2a0780f7abcde10flvxn.42 -> tuya.0.bf2a0780f7abcde10flvxn
     */
    fun extractDeviceBaseId(stateId: String, adapterId: String): String? {
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
    fun extractDeviceName(deviceBaseId: String, adapterId: String): String {
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
    fun extractDeviceNameFromObject(deviceBaseId: String, deviceObj: JsonObject?, adapterId: String): String {
        // Пробуем получить из common.name объекта
        val common = deviceObj?.get("common") as? JsonObject
        val name = JsonUtils.getString(common, "name")
        if (!name.isNullOrBlank()) return name

        // Fallback: извлекаем из ID
        return extractDeviceName(deviceBaseId, adapterId)
    }

    /**
     * Парсит state объект в Domain State
     */
    fun parseStateObject(obj: JsonObject): State {
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
}
