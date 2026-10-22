package com.besenok.smarthome.data.remote.iobroker

import android.util.Base64
import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.repository.ConnectionState
import com.besenok.smarthome.util.CryptoHelper
import com.besenok.smarthome.util.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IobrokerRestClient"
private const val REST_PORT = 8087

data class IobrokerState(
    val value: Any? = null,
    val ack: Boolean = false,
    val ts: Int = 0,
    val q: Int = 0,
    val from: String? = null,
    val user: String? = null,
    val lc: Int = 0
)

data class IobrokerObject(
    val _id: String? = null,
    val type: String? = null,
    val common: JsonObject? = null,
    val native: JsonObject? = null
)

data class IobrokerVersionResponse(
    val error: String? = null,
    val version: String? = null,
    val os: JsonObject? = null,
    @SerialName("node.js") val nodeJs: String? = null,
    @SerialName("npm") val npm: String? = null
)

@Singleton
class IobrokerRestClient @Inject constructor(
    private val preferences: AppPreferencesDataSource,
    private val rateLimiter: RateLimiter,
    // Единый клиент на всё приложение (пул соединений и потоки не пересоздаются)
    private val httpClient: HttpClient
) {

    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var isLoggedIn = false

    private var reconnectJob: Job? = null
    private var keepAliveJob: Job? = null
    private var currentSession: String? = null

    /**
     * Создает Basic Auth заголовок из сохраненных учетных данных
     */
    private data class AuthCache(val username: String, val encryptedPassword: String, val header: String?)

    @Volatile
    private var authCache: AuthCache? = null

    /**
     * Создает Basic Auth заголовок из сохраненных учетных данных.
     * Заголовок кешируется: расшифровка (Keystore) выполняется только при изменении логина/пароля.
     */
    private suspend fun getBasicAuthHeader(): String? {
        return try {
            val username = preferences.iobrokerUsername.first()
            val encryptedPassword = preferences.iobrokerPassword.first()

            authCache?.let {
                if (it.username == username && it.encryptedPassword == encryptedPassword) return it.header
            }

            val password = CryptoHelper.decrypt(encryptedPassword)
            val header = if (username.isNotBlank() && password.isNotBlank()) {
                val encoded = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Basic $encoded"
            } else {
                Log.w(TAG, "Username or password is blank")
                null
            }
            authCache = AuthCache(username, encryptedPassword, header)
            header
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Basic Auth header", e)
            null
        }
    }

    /**
     * Строит запрос к REST API ioBroker с корректным кодированием пути и параметров
     */
    private fun HttpRequestBuilder.restUrl(
        host: String,
        authHeader: String,
        vararg segments: String,
        params: List<Pair<String, String>> = emptyList()
    ) {
        url {
            protocol = URLProtocol.HTTPS
            this.host = host
            port = REST_PORT
            appendPathSegments(*segments, encodeSlash = true)
            params.forEach { (k, v) -> parameters.append(k, v) }
        }
        header(HttpHeaders.Authorization, authHeader)
        header(HttpHeaders.Accept, "application/json")
    }

    /** Значение для параметра value: строки - корректный JSON-литерал (с экранированием) */
    private fun encodeValue(value: Any): String = when (value) {
        is Boolean -> if (value) "true" else "false"
        is String -> JsonPrimitive(value).toString()
        else -> value.toString()
    }

    suspend fun connect() {
        // Не создаём новый клиент если уже подключены
        if (_connectionState.value is ConnectionState.Connected) return
        // Не пересоздаём клиент во время подключения
        if (_connectionState.value is ConnectionState.Connecting) return

        // Отменяем предыдущие попытки реконнекта
        reconnectJob?.cancel()
        keepAliveJob?.cancel()

        _connectionState.value = ConnectionState.Connecting

        try {
            val host = preferences.iobrokerHost.first()

            if (host.isBlank()) {
                _connectionState.value = ConnectionState.Error("Укажите адрес ioBroker в настройках")
                return
            }

            // Клиент уже настроен в NetworkModule (включая доверие самоподписанному сертификату)
            isLoggedIn = true
            currentSession = "session-${System.currentTimeMillis()}"

            // Аутентификация через Basic Auth
            val authHeader = getBasicAuthHeader()
            if (authHeader != null) {
                val response = httpClient.get {
                    restUrl(host, authHeader, "json", "get", params = listOf("get" to "system.adapter.admin.0.alive"))
                }
                
                if (response.status.value in 200..299) {
                    _connectionState.value = ConnectionState.Connected("Connected to $host:$REST_PORT")
                    startKeepAlive()
                } else {
                    isLoggedIn = false
                    _connectionState.value = ConnectionState.Error("Аутентификация не удалась")
                    startReconnection()
                }
            } else {
                isLoggedIn = false
                _connectionState.value = ConnectionState.Error("Укажите учетные данные в настройках")
                startReconnection()
            }
        } catch (e: Exception) {
            isLoggedIn = false
            _connectionState.value = ConnectionState.Error("Ошибка подключения: ${e.message}")
            startReconnection()
        }
    }

    private fun startKeepAlive() {
        // Не создаём несколько keep-alive циклов
        if (keepAliveJob?.isActive == true) return
        
        keepAliveJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                delay(30000)
                
                try {
                    val host = preferences.iobrokerHost.first()
                    
                    val authHeader = getBasicAuthHeader()
                    if (authHeader != null) {
                        try {
                            httpClient.get {
                                restUrl(host, authHeader, "json", "get", params = listOf("get" to "system.adapter.admin.0.alive"))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Keep-alive check failed, will try reconnect")
                            // Не меняем состояние напрямую, даём reconnectJob обработать
                            break
                        }
                    } else {
                        Log.w(TAG, "Auth header lost during keep-alive")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Keep-alive exception: ${e.message}")
                    break
                }
            }
            
            // Keep-alive завершён — запускаем реконнект если нужно
            if (_connectionState.value !is ConnectionState.Connected) {
                startReconnection()
            }
        }
    }

    suspend fun getState(objectId: String): IobrokerState? {
        return try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for getState")
                return null
            }
            
            val response =             httpClient.get {
                restUrl(host, authHeader, "json", "get", params = listOf("get" to objectId))
            }

            if (response.status.value !in 200..299) {
                val errorBody = response.body<String>()
                Log.e(TAG, "getState($objectId) failed: status=${response.status}, body=$errorBody")
                return null
            }

            // ioBroker /json/get возвращает JSON: {"val":value,"ack":true,...}
            val jsonBody = response.body<JsonObject>()
            
            // Проверяем наличие поля "val"
            if (!jsonBody.contains("val")) {
                Log.w(TAG, "getState($objectId): 'val' field not found in response. Keys: ${jsonBody.keys}")
                return null
            }
            
            val rawValue = jsonBody["val"]?.let {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull
                    else -> it.toString()
                }
            }
            
            Log.d(TAG, "getState($objectId) = rawValue=$rawValue, type=${jsonBody["val"]?.javaClass?.simpleName}")
            
            val value = if (rawValue == null || rawValue == "null" || rawValue == "") {
                null
            } else {
                parsePlainValue(rawValue)
            }
            IobrokerState(value = value, ack = true, ts = System.currentTimeMillis().toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting state for $objectId", e)
            null
        }
    }
    
    private fun parsePlainValue(plainValue: String): Any? {
        return when (plainValue) {
            "null", "" -> null
            "true" -> true
            "false" -> false
            else -> try {
                plainValue.toDouble()
            } catch (e: NumberFormatException) {
                plainValue
            }
        }
    }

    suspend fun setState(objectId: String, value: Any) {
        try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for setState")
                throw Exception("No valid auth credentials")
            }
            
            val response =             httpClient.get {
                restUrl(
                    host, authHeader, "json", "set", objectId,
                    params = listOf("value" to encodeValue(value))
                )
            }
            
            Log.d(TAG, "setState REST response status: ${response.status}")
            
            if (response.status.value !in 200..299) {
                val errorBody = response.body<String>()
                Log.e(TAG, "setState REST failed: status=${response.status}")
                throw Exception("setState REST failed: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting state for $objectId via REST", e)
            throw e
        }
    }
    
    suspend fun setStateWebSocket(objectId: String, value: Any, ack: Boolean = true) {
        try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for setStateWebSocket")
                throw Exception("No valid auth credentials")
            }
            
            val response =             httpClient.get {
                restUrl(
                    host, authHeader, "set", objectId,
                    params = listOf("value" to encodeValue(value), "ack" to ack.toString())
                )
            }
            
            Log.d(TAG, "setState REST response status: ${response.status}")
            
            if (response.status.value !in 200..299) {
                val errorBody = response.body<String>()
                Log.e(TAG, "setState REST failed: status=${response.status}")
                throw Exception("setState REST failed: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting state for $objectId via REST", e)
            throw e
        }
    }

    suspend fun getObject(objectId: String): JsonObject? {
        return try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for getObject")
                return null
            }
            
            val response: JsonObject =             httpClient.get {
                restUrl(host, authHeader, "json", "get", objectId)
            }.body()
            
            Log.d(TAG, "getObject($objectId) = ${response.keys}")
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error getting object $objectId", e)
            null
        }
    }
    
    suspend fun getObjects(pattern: String = "*", generate: Boolean = false): JsonObject? {
        Log.d(TAG, "getObjects called with pattern: $pattern")
        
        return try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) {
                Log.w(TAG, "Not connected, cannot make REST API call")
                return null
            }
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for getObjects")
                return null
            }
            
            val response: JsonObject =             httpClient.get {
                restUrl(host, authHeader, "objects", params = listOf("pattern" to pattern))
            }.body()
            
            Log.d(TAG, "Raw response keys: ${response.keys.take(10)}")
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error getting objects", e)
            null
        }
    }

    suspend fun getStates(pattern: String = "*"): JsonObject? {
        Log.d(TAG, "getStates called with pattern: $pattern")
        
        return try {
            rateLimiter.acquire()
            
            if (!isLoggedIn) {
                Log.w(TAG, "Not connected, cannot make REST API call")
                return null
            }
            val host = preferences.iobrokerHost.first()
            
            val authHeader = getBasicAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "No valid auth credentials for getStates")
                return null
            }
            
            val response: JsonObject =             httpClient.get {
                restUrl(host, authHeader, "states", params = listOf("pattern" to pattern))
            }.body()
            
            Log.d(TAG, "Raw states response keys: ${response.keys.take(10)}")
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error getting states", e)
            null
        }
    }

    suspend fun disconnect() {
        // Отменяем все корутины
        reconnectJob?.cancel()
        keepAliveJob?.cancel()
        reconnectJob = null
        keepAliveJob = null

        // Общий HttpClient не закрываем: он живёт всё время работы приложения
        currentSession = null
        isLoggedIn = false
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun startReconnection() {
        // Не создаём несколько циклов реконнекта
        if (reconnectJob?.isActive == true) return
        // Отменяем keep-alive при реконнекте
        keepAliveJob?.cancel()

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var delayMs = 1000L
            val maxDelayMs = 30000L

            while (isActive) {
                // Проверяем состояние перед каждой попыткой
                when (_connectionState.value) {
                    is ConnectionState.Connected -> break
                    is ConnectionState.Error -> {
                        // Если ошибка подключения (не аутентификация), пробуем reconnect
                        val errorMsg = (_connectionState.value as ConnectionState.Error).message ?: ""
                        if (errorMsg.contains("адрес") || errorMsg.contains("учетные данные")) {
                            // Фатальные ошибки — не переподключаемся
                            break
                        }
                    }
                    else -> {}
                }

                delay(delayMs)
                delayMs = minOf(delayMs * 2, maxDelayMs)
                
                // connect() сам проверит состояние и не создаст дубликаты
                connect()
            }
        }
    }
}
