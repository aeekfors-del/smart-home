package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import com.besenok.smarthome.domain.model.ControlType
import com.besenok.smarthome.domain.model.Device
import com.besenok.smarthome.util.TypeMapper
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceClassifier"

/**
 * Классификатор устройств по каналам
 * Ответственность: weighted scoring алгоритм определения типа устройства
 */
@Singleton
class DeviceClassifier @Inject constructor() {

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
    fun classify(
        channels: List<Device.Channel>,
        fallbackName: String
    ): Device.DeviceType {
        if (channels.isEmpty()) {
            Log.w(TAG, "No channels for device $fallbackName, using fallback name-based detection")
            return TypeMapper.mapDeviceTypeFromName(fallbackName)
        }

        val channelNames = channels.map { it.name.lowercase() }.toSet()
        val has = { predicate: (String) -> Boolean -> channelNames.any(predicate) }

        var bestType: Device.DeviceType? = null
        var maxScore = 0

        // 1. THERMOSTAT: temperature + setpoint/hysteresis/heating
        val hasTemperature = has { it.contains("temperature") || it.contains("temp") || it.contains("температур") }
        val hasSetpoint = has { it.contains("setpoint") || it.contains("hysteresis") || it.contains("target") ||
            it.contains("зона") || it.contains("задач") }
        val hasHeating = has { it.contains("heating") || it.contains("обогрев") || it.contains("нагрев") }

        if ((hasTemperature && hasSetpoint) || (hasTemperature && hasHeating)) {
            bestType = Device.DeviceType.THERMOSTAT
            maxScore = 10
        }

        // 2. BLIND: curtain/blind/jalousie/roll
        val hasBlind = has { it.contains("blind") || it.contains("curtain") || it.contains("jalousie") ||
            it.contains("roll") || it.contains("штор") || it.contains("жало") || it.contains("рулон") }
        val hasLevel = has { it.contains("level") || it.contains("brightness") || it.contains("ярк") }

        if (hasBlind) {
            if (maxScore < 9) {
                bestType = Device.DeviceType.BLIND
                maxScore = 9
            }
        } else if (hasLevel && channels.size <= 3) {
            // Level без color может быть blind
            if (maxScore < 5) {
                bestType = Device.DeviceType.BLIND
                maxScore = 5
            }
        }

        // 3. GATE: gate/garage/door
        val hasGate = has { it.contains("gate") || it.contains("garage") || it.contains("ворота") ||
            it.contains("двер") || it.contains("portal") }

        if (hasGate) {
            if (maxScore < 9) {
                bestType = Device.DeviceType.GATE
                maxScore = 9
            }
        }

        // 4. LIGHT: switch.light/dimmer/color
        val hasLightSwitch = has { it.contains("light.on") || it.contains("light_switch") || it.contains("свет") }
        val hasDimmer = has { it.contains("dimmer") || it.contains("dim") || it.contains("ярк") }
        val hasColor = has { it.contains("color") || it.contains("rgb") || it.contains("Цвет") }
        val hasSwitch = has { it.contains("switch") || it.contains("переключ") }

        if ((hasLightSwitch || hasDimmer || hasColor) && maxScore < 8) {
            bestType = Device.DeviceType.LIGHT
            maxScore = 8
        } else if (hasSwitch && !has { it.contains("socket") || it.contains("outlet") || it.contains("розетк") } && maxScore < 4) {
            // Простой switch без socket - может быть light
            if (hasLevel || channels.any { it.controlType == ControlType.SLIDER }) {
                bestType = Device.DeviceType.LIGHT
                maxScore = 4
            }
        }

        // 5. SOCKET: socket/outlet/main switch
        val hasSocket = has { it.contains("socket") || it.contains("outlet") || it.contains("розетк") }

        if (hasSocket && maxScore < 7) {
            bestType = Device.DeviceType.SOCKET
            maxScore = 7
        }

        // 6. SENSOR: sensor roles (motion, magnet, illumination, temperature, etc.)
        val hasMotion = has { it.contains("motion") || it.contains("movement") || it.contains("движ") ||
            it.contains("pir") || it.contains("присутств") }
        val hasMagnet = has { it.contains("magnet") || it.contains("contact") || it.contains("открыв") ||
            it.contains("закрыт") || it.contains("window") || it.contains("door_sensor") }
        val hasIllumination = has { it.contains("illumination") || it.contains("lux") || it.contains("освещ") }
        val hasHumidity = has { it.contains("humidity") || it.contains("влажн") }
        val hasPressure = has { it.contains("pressure") || it.contains("давлен") }
        val hasWater = has { it.contains("water") || it.contains("течь") || it.contains("поток") }
        val hasGas = has { it.contains("gas") || it.contains("газ") }
        val hasSmoke = has { it.contains("smoke") || it.contains("дым") }

        val sensorKeywordsCount = listOf(
            hasMotion, hasMagnet, hasIllumination, hasHumidity, hasPressure,
            hasWater, hasGas, hasSmoke
        ).count { it }

        if (sensorKeywordsCount > 0 && maxScore < sensorKeywordsCount + 2) {
            bestType = Device.DeviceType.SENSOR
            maxScore = sensorKeywordsCount + 2
        }

        // 6b. Tuya-сенсоры: voltage, percent, temperature, doublePress, click, state
        val hasVoltage = has { it.contains("voltage") }
        val hasPercent = has { it.contains("percent") || it.contains("battery") }
        val hasDoublePress = has { it.contains("doublepress") || it.contains("double_press") || it.contains("double") }
        val hasClick = has { it.contains("click") || it.contains("button") }
        val hasState = has { it.contains(".state") || it.contains("state") }

        val tuyaSensorCount = listOf(hasVoltage, hasPercent, hasDoublePress, hasClick, hasState).count { it }

        if (tuyaSensorCount > 0 && maxScore < tuyaSensorCount + 1) {
            // Tuya-устройства с voltage/percent/doublePress/click - это сенсоры
            bestType = Device.DeviceType.SENSOR
            maxScore = tuyaSensorCount + 1
            Log.d(TAG, "  🔌 Detected TUYA SENSOR by channel names: voltage=$hasVoltage percent=$hasPercent doublePress=$hasDoublePress click=$hasClick state=$hasState")
        }

        // 7. Tuya-устройства с числовыми каналами (DP IDs: 1, 9, 17, 18, 19...)
        val numericNames = channels.count { it.name.matches(Regex("^\\d+$")) }
        val numericNamesWithDash = channels.count { it.name.matches(Regex("^\\d+-\\d+$")) }
        val totalNumericNames = numericNames + numericNamesWithDash

        if (totalNumericNames > channels.size / 2 && channels.size > 5) {
            // Больше 50% каналов - числа, это Tuya устройство
            if (maxScore < 6) {
                bestType = Device.DeviceType.SOCKET
                maxScore = 6
                Log.d(TAG, "  🔌 Detected TUYA SOCKET by numeric DP IDs: $totalNumericNames numeric channels out of ${channels.size}")
            }
        }

        // 8. Устройства с числовыми каналами (MQTT водосчётчик и т.п.)
        val hasCurrent = has { it.contains("current") || it.contains(".i") || it.contains("ток") }
        val hasPower = has { it.contains("power") || it.contains(".w") || it.contains("мощн") }
        val hasEnergy = has { it.contains("energy") || it.contains(".wh") || it.contains("энерги") }
        val hasMeter = has { it.contains("meter") || it.contains("счётчик") || it.contains("metric") }
        val hasCold = has { it.contains("cold") || it.contains("hot_water") || it.contains("холод") }
        val hasHot = has { it.contains("hot") && !it.contains("heating") && !it.contains("hot_water") || it.contains("горяч") }

        val numericChannelCount = listOf(hasCurrent, hasPower, hasEnergy, hasMeter, hasCold, hasHot).count { it }

        if (numericChannelCount > 2 && maxScore < numericChannelCount + 1) {
            // Устройство с множеством числовых каналов - это SENSOR (счётчики, метрики)
            bestType = Device.DeviceType.SENSOR
            maxScore = numericChannelCount + 1
            Log.d(TAG, "  📊 Detected SENSOR by numeric channels: i=$hasCurrent w=$hasPower wh=$hasEnergy meter=$hasMeter")
        }

        // Fallback: используем определение по имени
        val finalType = bestType ?: TypeMapper.mapDeviceTypeFromName(fallbackName)
        Log.d(TAG, "  🎯 Final device type for '$fallbackName': $finalType (score=$maxScore, channels=${channels.size})")
        return finalType
    }

}
