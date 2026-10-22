package com.besenok.smarthome.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.besenok.smarthome.BuildConfig
import com.besenok.smarthome.util.CryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferencesDataSource(private val context: Context) {

    companion object {
        // ioBroker settings
        val IOBROKER_HOST = stringPreferencesKey("iobroker_host")
        val IOBROKER_PORT = stringPreferencesKey("iobroker_port")
        val IOBROKER_USERNAME = stringPreferencesKey("iobroker_username")
        val IOBROKER_PASSWORD = stringPreferencesKey("iobroker_password")

        // Weather settings
        val WEATHER_REGION = stringPreferencesKey("weather_region")
        val OPENWEATHERMAP_API_KEY = stringPreferencesKey("openweathermap_api_key")

        // Theme settings
        val APP_THEME = stringPreferencesKey("app_theme")

        // Auth
        val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")

        // Defaults (секреты в коде не хранятся: значения задаются через local.properties/BuildConfig)
        val DEFAULT_HOST: String = BuildConfig.IOBROKER_DEFAULT_HOST
        val DEFAULT_PORT = "8081"
        val DEFAULT_WEATHER_REGION = "55.7558,37.6173" // Moscow
        val DEFAULT_APP_THEME = "SYSTEM" // LIGHT, DARK, SYSTEM
        val DEFAULT_OPENWEATHERMAP_API_KEY: String = BuildConfig.OPENWEATHERMAP_API_KEY

        // Device names storage keys
        const val DEVICE_NAMES_PREFIX = "device_name_"

        // Parameter (channel) names storage keys
        const val PARAMETER_NAMES_PREFIX = "parameter_name_"
        
        // Added adapters storage
        val ADAPTERS_LIST = stringPreferencesKey("added_adapters")
        val DEVICE_IDS_LIST = stringPreferencesKey("added_device_ids")

        // Dashboard: objectId выбранного параметра для каждой ячейки row2
        const val DASHBOARD_SLOT_COUNT = 3
        const val DASHBOARD_SLOT_PREFIX = "dashboard_slot_"

        fun dashboardSlotKey(index: Int) = stringPreferencesKey("$DASHBOARD_SLOT_PREFIX$index")

        // Weather regions: name -> lat,lon
        val WEATHER_REGIONS = mapOf(
            "Москва" to "55.7558,37.6173",
            "Санкт-Петербург" to "59.9343,30.3351",
            "Новосибирск" to "55.0084,82.9357",
            "Екатеринбург" to "56.8389,60.6057",
            "Казань" to "55.7961,49.1064",
            "Нижний Новгород" to "56.2965,43.9361",
            "Владивосток" to "43.1155,131.8855",
            "Красноярск" to "56.0153,92.8932",
            "Ростов-на-Дону" to "47.2357,39.7015",
            "Самара" to "53.1959,50.1002",
            "Уфа" to "54.7388,55.9721",
            "Омск" to "54.9885,73.3242",
            "Челябинск" to "55.1644,61.4368",
            "Пермь" to "58.0104,56.2294",
            "Волгоград" to "48.7080,44.5133",
            "Саратов" to "51.5462,46.0154",
            "Тюмень" to "57.1553,65.5619",
            "Ульяновск" to "54.3917,48.4789",
            "Киев" to "50.4501,30.5234",
            "Минск" to "53.9045,27.5615",
            "Алматы" to "43.2220,76.8512",
            "Ташкент" to "41.2995,69.2401",
            "Бишкек" to "42.8746,74.5698",
            "Калининград" to "54.7104,20.4522",
            "Севастополь" to "44.5681,33.5246",
            "Симферополь" to "44.9521,34.1024",
            "Махачкала" to "42.9849,47.5047",
            "Грозный" to "43.3179,45.6981",
            "Нальчик" to "43.4823,43.5927",
            "Якутск" to "62.0355,129.6755",
            "Иркутск" to "52.2970,104.2964",
            "Чита" to "52.0335,113.4772",
            "Кемерово" to "55.3333,86.0833",
            "Новокузнецк" to "53.7596,87.1216",
            "Тверь" to "56.8587,35.9176",
            "Смоленск" to "54.7826,32.0457",
            "Псков" to "57.8194,28.3318",
            "Воронеж" to "51.6683,39.1840",
            "Саранск" to "54.1838,45.1749",
            "Йошкар-Ола" to "56.6326,47.8919",
            "Чебоксары" to "56.1322,47.2519",
            "Кострома" to "57.7667,40.9667",
            "Иваново" to "56.9865,40.9819",
            "Ярославль" to "57.6261,39.8845",
            "Тула" to "54.1961,37.6182",
            "Калуга" to "54.5293,36.2724",
            "Рязань" to "54.6269,39.6916",
            "Курск" to "51.7373,36.1874",
            "Белгород" to "50.6129,36.5835",
            "Орёл" to "52.9673,36.0765",
            "Брянск" to "53.2434,34.3645",
            "Владимир" to "56.1290,40.4060",
            "Пенза" to "53.2001,45.0046",
            "Ставрополь" to "45.0428,41.9734"
        )
    }

    val iobrokerHost: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IOBROKER_HOST] ?: DEFAULT_HOST
    }

    val iobrokerPort: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IOBROKER_PORT] ?: DEFAULT_PORT
    }

    val iobrokerUsername: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IOBROKER_USERNAME] ?: ""
    }

    val iobrokerPassword: Flow<String> = context.dataStore.data.map { preferences ->
        // Зашифрованное значение. Только для внутреннего использования сетевым слоем (расшифровка в CryptoHelper)
        preferences[IOBROKER_PASSWORD] ?: ""
    }

    /** Задан ли пароль (сам пароль/шифртекст в UI не передаётся) */
    val hasIobrokerPassword: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[IOBROKER_PASSWORD].isNullOrEmpty()
    }

    val weatherRegion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEATHER_REGION] ?: DEFAULT_WEATHER_REGION
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: DEFAULT_APP_THEME
    }

    val openweathermapApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        // Ключ по умолчанию из BuildConfig в UI не показываем; fallback применяется в WeatherApiService
        preferences[OPENWEATHERMAP_API_KEY] ?: ""
    }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_AUTHENTICATED] ?: false
    }

    /**
     * @param password новый пароль в открытом виде; пустая строка = не менять сохранённый
     */
    suspend fun saveIoBrokerSettings(host: String, port: String, username: String, password: String) {
        val encryptedPassword = if (password.isNotEmpty()) CryptoHelper.encrypt(password) else null
        context.dataStore.edit { preferences ->
            preferences[IOBROKER_HOST] = host
            preferences[IOBROKER_PORT] = port
            preferences[IOBROKER_USERNAME] = username
            if (encryptedPassword != null) {
                preferences[IOBROKER_PASSWORD] = encryptedPassword
            }
        }
    }

    suspend fun saveWeatherRegion(regionName: String) {
        val coords = WEATHER_REGIONS[regionName] ?: DEFAULT_WEATHER_REGION
        context.dataStore.edit { preferences ->
            preferences[WEATHER_REGION] = coords
        }
    }

    suspend fun saveAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }

    suspend fun saveOpenWeatherMapApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[OPENWEATHERMAP_API_KEY] = apiKey
        }
    }

    suspend fun setAuthenticated(authenticated: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_AUTHENTICATED] = authenticated
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // ========== Device Names Storage ==========

    /**
     * Сохраняет пользовательское имя устройства
     * @param deviceId ID устройства
     * @param customName Пользовательское имя (null для удаления)
     */
    suspend fun saveDeviceName(deviceId: String, customName: String?) {
        withContext(Dispatchers.IO) {
            val key = stringPreferencesKey(DEVICE_NAMES_PREFIX + deviceId)
            context.dataStore.edit { preferences ->
                if (customName != null) {
                    preferences[key] = customName
                } else {
                    preferences.remove(key)
                }
            }
        }
    }

    /**
     * Получает пользовательское имя устройства
     * @param deviceId ID устройства
     * @return Пользовательское имя или null если не сохранено
     */
    suspend fun getCustomDeviceName(deviceId: String): String? {
        return context.dataStore.data.first()[stringPreferencesKey(DEVICE_NAMES_PREFIX + deviceId)]
    }

    /**
     * Получает все сохранённые имена устройств
     * @return Map где ключ - deviceId, значение - имя
     */
    suspend fun getAllDeviceNames(): Map<String, String> {
        val preferences = context.dataStore.data.first()
        return preferences.asMap()
            .filterKeys { (it as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.startsWith(DEVICE_NAMES_PREFIX) == true }
            .mapValues { it.value as? String ?: "" }
            .filter { it.value.isNotEmpty() }
            .mapKeys { (it.key as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.substring(DEVICE_NAMES_PREFIX.length) ?: "" }
    }

    /**
     * Удаляет пользовательское имя устройства (возвращает к оригинальному)
     * @param deviceId ID устройства
     */
    suspend fun deleteDeviceName(deviceId: String) {
        saveDeviceName(deviceId, null)
    }

    // ========== Dashboard slots ==========

    /** Выбранные параметры для ячеек row2 (null - ячейка не настроена) */
    val dashboardSlots: Flow<List<String?>> = context.dataStore.data.map { preferences ->
        (0 until DASHBOARD_SLOT_COUNT).map { index -> preferences[dashboardSlotKey(index)] }
    }

    /**
     * Сохраняет параметр для ячейки dashboard
     * @param objectId ID состояния ioBroker, null или пустая строка - очистить ячейку
     */
    suspend fun saveDashboardSlot(index: Int, objectId: String?) {
        context.dataStore.edit { preferences ->
            val key = dashboardSlotKey(index)
            if (objectId.isNullOrBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = objectId
            }
        }
    }

    // ========== Parameter (channel) Names Storage ==========

    /**
     * Сохраняет пользовательское имя параметра (канала)
     * @param objectId ID состояния ioBroker (например, mihome.0.devices.sensor_ht_1.temperature)
     * @param customName Пользовательское имя (null для удаления)
     */
    suspend fun saveParameterName(objectId: String, customName: String?) {
        withContext(Dispatchers.IO) {
            val key = stringPreferencesKey(PARAMETER_NAMES_PREFIX + objectId)
            context.dataStore.edit { preferences ->
                if (customName != null) {
                    preferences[key] = customName
                } else {
                    preferences.remove(key)
                }
            }
        }
    }

    /**
     * Получает пользовательское имя параметра
     * @return Пользовательское имя или null если не сохранено
     */
    suspend fun getCustomParameterName(objectId: String): String? {
        return context.dataStore.data.first()[stringPreferencesKey(PARAMETER_NAMES_PREFIX + objectId)]
    }

    /**
     * Пользовательские имена параметров в виде потока: objectId -> имя.
     * Позволяет UI обновляться сразу после переименования.
     */
    val parameterNames: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        preferences.asMap()
            .filterKeys { (it as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.startsWith(PARAMETER_NAMES_PREFIX) == true }
            .mapValues { it.value as? String ?: "" }
            .filter { it.value.isNotEmpty() }
            .mapKeys { (it.key as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.substring(PARAMETER_NAMES_PREFIX.length) ?: "" }
    }

    /**
     * Получает все сохранённые имена параметров
     * @return Map где ключ - objectId параметра, значение - имя
     */
    suspend fun getAllParameterNames(): Map<String, String> {
        val preferences = context.dataStore.data.first()
        return preferences.asMap()
            .filterKeys { (it as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.startsWith(PARAMETER_NAMES_PREFIX) == true }
            .mapValues { it.value as? String ?: "" }
            .filter { it.value.isNotEmpty() }
            .mapKeys { (it.key as? androidx.datastore.preferences.core.Preferences.Key<*>)?.name?.substring(PARAMETER_NAMES_PREFIX.length) ?: "" }
    }

    /**
     * Удаляет пользовательское имя параметра (возвращает к оригинальному)
     */
    suspend fun deleteParameterName(objectId: String) {
        saveParameterName(objectId, null)
    }

    // ========== Added Adapters Storage ==========

    /**
     * Сохраняет список deviceIds добавленных устройств (через ", ")
     * @param deviceIds Список ID устройств
     */
    suspend fun saveAddedDeviceIds(deviceIds: List<String>) {
        context.dataStore.edit { preferences ->
            if (deviceIds.isNotEmpty()) {
                preferences[DEVICE_IDS_LIST] = deviceIds.joinToString(", ")
            } else {
                preferences.remove(DEVICE_IDS_LIST)
            }
        }
    }

    /**
     * Получает список добавленных deviceIds
     * @return Список deviceId
     */
    suspend fun getAddedDeviceIds(): List<String> {
        val prefs = context.dataStore.data.first()
        val deviceIdsStr = prefs[DEVICE_IDS_LIST]
        return if (!deviceIdsStr.isNullOrBlank()) {
            deviceIdsStr.split(", ").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    /**
     * Добавляет device в список добавленных (атомарно, без гонок read-modify-write)
     * @param deviceId ID устройства
     */
    suspend fun addDeviceToList(deviceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[DEVICE_IDS_LIST]
                ?.split(", ")?.filter { it.isNotBlank() }
                ?: emptyList()
            if (deviceId !in current) {
                preferences[DEVICE_IDS_LIST] = (current + deviceId).joinToString(", ")
            }
        }
    }

    /**
     * Удаляет device из списка добавленных
     * @param deviceId ID устройства
     */
    suspend fun removeDeviceFromList(deviceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[DEVICE_IDS_LIST]
                ?.split(", ")?.filter { it.isNotBlank() }
                ?: emptyList()
            val newList = current.filter { it != deviceId }
            if (newList.isEmpty()) {
                preferences.remove(DEVICE_IDS_LIST)
            } else {
                preferences[DEVICE_IDS_LIST] = newList.joinToString(", ")
            }
        }
    }

    /**
     * Сохраняет список adapterId добавленных адаптеров (через ", ")
     * @param adapterIds Список ID адаптеров (например, "mqtt.0, mihome.0")
     */
    suspend fun saveAddedAdapters(adapterIds: List<String>) {
        context.dataStore.edit { preferences ->
            if (adapterIds.isNotEmpty()) {
                preferences[ADAPTERS_LIST] = adapterIds.joinToString(", ")
            } else {
                preferences.remove(ADAPTERS_LIST)
            }
        }
    }

    /**
     * Получает список добавленных адаптеров
     * @return Список adapterId
     */
    suspend fun getAddedAdapterIds(): List<String> {
        val prefs = context.dataStore.data.first()
        val adaptersStr = prefs[ADAPTERS_LIST]
        return if (!adaptersStr.isNullOrBlank()) {
            adaptersStr.split(", ").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    /**
     * Добавляет адаптер в список добавленных (атомарно)
     * @param adapterId ID адаптера
     */
    suspend fun addAdapterToList(adapterId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[ADAPTERS_LIST]
                ?.split(", ")?.filter { it.isNotBlank() }
                ?: emptyList()
            if (adapterId !in current) {
                preferences[ADAPTERS_LIST] = (current + adapterId).joinToString(", ")
            }
        }
    }

    /**
     * Удаляет адаптер из списка добавленных
     * @param adapterId ID адаптера
     */
    suspend fun removeAdapterFromList(adapterId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[ADAPTERS_LIST]
                ?.split(", ")?.filter { it.isNotBlank() }
                ?: emptyList()
            val newList = current.filter { it != adapterId }
            if (newList.isEmpty()) {
                preferences.remove(ADAPTERS_LIST)
            } else {
                preferences[ADAPTERS_LIST] = newList.joinToString(", ")
            }
        }
    }
}
