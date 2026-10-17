package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.repository.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "IobrokerWebSocketClient"

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
class IobrokerWebSocketClient @Inject constructor(
    private val preferences: AppPreferencesDataSource,
    private val json: Json
) {

    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var reconnectJob: Job? = null
    private var currentSession: String? = null
    private var httpClient: HttpClient? = null
    private var isLoggedIn = false

    suspend fun connect() {
        if (_connectionState.value is ConnectionState.Connected) return

        _connectionState.value = ConnectionState.Connecting

        try {
            val host = preferences.iobrokerHost.first()
            val port = preferences.iobrokerPort.first()
            val username = preferences.iobrokerUsername.first()
            val password = preferences.iobrokerPassword.first()

            if (host.isBlank()) {
                _connectionState.value = ConnectionState.Error("Укажите адрес ioBroker в настройках")
                return
            }

            // Use port 8087 for REST API
            val restPort = "8087"

            // Create HTTP client for REST API (HTTPS with self-signed cert support)
            val sslContext = SSLContext.getInstance("TLS")
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                }
            )
            sslContext.init(null, trustAllCerts, SecureRandom())

            val client = HttpClient(OkHttp) {
                engine {
                    config {
                        sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                        hostnameVerifier { hostname, _ -> true }
                        connectTimeout(10, TimeUnit.SECONDS)
                        readTimeout(10, TimeUnit.SECONDS)
                    }
                }
                install(ContentNegotiation) {
                    json(json)
                }
            }

            httpClient = client
            currentSession = "session-${System.currentTimeMillis()}"

            // Test connection via REST API on port 8087
            val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            
            try {
                val response = client.get("https://$host:$restPort/json/getPlainValue/system.adapter.admin.0.alive") {
                    header(HttpHeaders.Authorization, "Basic $credentials")
                    header(HttpHeaders.ContentType, "application/json")
                    header(HttpHeaders.Accept, "application/json")
                }
                
                if (response.status.value in 200..299) {
                    isLoggedIn = true
                    _connectionState.value = ConnectionState.Connected("Connected to $host:$restPort")
                    startKeepAlive()
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Не удалось подключиться к ioBroker: ${e.message}")
                startReconnection()
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Ошибка подключения: ${e.message}")
            startReconnection()
        }
    }

    private fun startKeepAlive() {
        if (reconnectJob?.isActive == true) return
        
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                delay(30000)
                
                try {
                    val client = httpClient ?: continue
                    val host = preferences.iobrokerHost.first()
                    val restPort = "8087"
                    val username = preferences.iobrokerUsername.first()
                    val password = preferences.iobrokerPassword.first()
                    val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                    
                    try {
                        client.get("https://$host:$restPort/json/getPlainValue/system.adapter.admin.0.alive") {
                            header(HttpHeaders.Authorization, "Basic $credentials")
                            header(HttpHeaders.ContentType, "application/json")
                            header(HttpHeaders.Accept, "application/json")
                        }
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.Error("Потеряно соединение")
                        startReconnection()
                        break
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error("Ошибка keep-alive: ${e.message}")
                    startReconnection()
                    break
                }
            }
        }
    }

    /**
     * Get state value via REST API
     * Endpoint: GET /json/getPlainValue/{stateID}
     * Result: true / false / 22.5 / "text"
     */
    suspend fun getState(objectId: String): IobrokerState? {
        val client = httpClient ?: throw IllegalStateException("Not connected")
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        return try {
            val plainValue: String = client.get("https://$host:$restPort/json/getPlainValue/$objectId") {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            
            Log.d("IobrokerWebSocketClient", "getState($objectId) = $plainValue")
            
            val value = parsePlainValue(plainValue)
            IobrokerState(value = value, ack = true, ts = System.currentTimeMillis().toInt())
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error getting state for $objectId", e)
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

    /**
     * Set state value via REST API
     * Endpoint: GET /json/set/{stateID}?value=X
     * Result: {"id": "...", "value": X}
     */
    suspend fun setState(objectId: String, value: Any) {
        val client = httpClient ?: throw IllegalStateException("Not connected")
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        
        val valueStr = when (value) {
            is Boolean -> if (value) "true" else "false"
            is String -> "\"$value\""
            else -> value.toString()
        }

        // URL encode the objectId
        val encodedObjectId = java.net.URLEncoder.encode(objectId, "UTF-8")
        val url = "https://$host:$restPort/json/set/$encodedObjectId?value=$valueStr"
        
        try {
            Log.d(TAG, "setState REST URL: $url")
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
            }
            
            Log.d(TAG, "setState REST response status: ${response.status}")
            
            if (response.status.value !in 200..299) {
                val errorBody = response.body<String>() ?: "Unknown error"
                Log.e(TAG, "setState REST failed: $errorBody")
                throw Exception("setState REST failed: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error setting state for $objectId via REST", e)
            throw e
        }
    }
    
    /**
     * Set state value via WebSocket (более надёжный метод)
     */
    suspend fun setStateWebSocket(objectId: String, value: Any, ack: Boolean = true) {
        val client = httpClient ?: throw IllegalStateException("Not connected")
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        
        // Используем setObject с правильным форматом
        val valueJson = when (value) {
            is Boolean -> if (value) "true" else "false"
            is String -> "\"$value\""
            else -> value.toString()
        }
        
        val encodedObjectId = java.net.URLEncoder.encode(objectId, "UTF-8")
        val url = "https://$host:$restPort/set/$encodedObjectId?value=$valueJson&ack=$ack"
        
        try {
            Log.d(TAG, "setState WebSocket URL: $url")
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
            }
            
            Log.d(TAG, "setState WebSocket response status: ${response.status}")
            
            if (response.status.value !in 200..299) {
                val errorBody = response.body<String>() ?: "Unknown error"
                Log.e(TAG, "setState WebSocket failed: $errorBody")
                throw Exception("setState WebSocket failed: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error setting state for $objectId via WebSocket", e)
            throw e
        }
    }

    /**
     * Get object and state via REST API
     * Endpoint: GET /json/get/{objectID}
     * Result: {"val": true, "ack": true, "ts": 1442432193, "_id": "...", "type": "state", "common": {...}, "native": {}}
     */
    suspend fun getObject(objectId: String): JsonObject? {
        val client = httpClient ?: throw IllegalStateException("Not connected")
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        return try {
            val response: JsonObject = client.get("https://$host:$restPort/json/get/$objectId") {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            
            Log.d("IobrokerWebSocketClient", "getObject($objectId) = ${response.keys}")
            
            response
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error getting object $objectId", e)
            null
        }
    }
    
    /**
     * Получение объектов через REST API
     * Endpoint: GET /objects?pattern=X
     * Result: JSON object с устройствами
     */
    suspend fun getObjects(pattern: String = "*", generate: Boolean = false): JsonObject? {
        Log.d("IobrokerWebSocketClient", "getObjects called with pattern: $pattern")
        val client = httpClient
        if (client == null) {
            Log.w("IobrokerWebSocketClient", "httpClient is null, cannot make REST API call")
            return null
        }
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        return try {
            // Используем /objects с заголовком Accept: application/json
            val response: JsonObject = client.get("https://$host:$restPort/objects?pattern=$pattern") {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            
            Log.d("IobrokerWebSocketClient", "Raw response keys: ${response.keys.take(10)}")
            
            response
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error getting objects", e)
            null
        }
    }

    /**
     * Получение состояний через REST API
     * Endpoint: GET /states?pattern=X
     * Result: {"system.adapter.admin.0.uptime": {"val": 32176, "ack": true, "ts": 1423156164, ...}}
     */
    suspend fun getStates(pattern: String = "*"): JsonObject? {
        Log.d("IobrokerWebSocketClient", "getStates called with pattern: $pattern")
        val client = httpClient
        if (client == null) {
            Log.w("IobrokerWebSocketClient", "httpClient is null, cannot make REST API call")
            return null
        }
        val host = preferences.iobrokerHost.first()
        val restPort = "8087"
        val username = preferences.iobrokerUsername.first()
        val password = preferences.iobrokerPassword.first()
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        return try {
            // Используем /states с заголовком Accept: application/json
            val response: JsonObject = client.get("https://$host:$restPort/states?pattern=$pattern") {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.Accept, "application/json")
            }.body()
            
            Log.d("IobrokerWebSocketClient", "Raw states response keys: ${response.keys.take(10)}")
            
            response
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error getting states", e)
            null
        }
    }

    suspend fun disconnect() {
        reconnectJob?.cancel()
        httpClient?.close()
        httpClient = null
        currentSession = null
        isLoggedIn = false
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun startReconnection() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var delayMs = 1000L
            val maxDelayMs = 30000L

            while (isActive && _connectionState.value !is ConnectionState.Connected) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, maxDelayMs)
                connect()
            }
        }
    }
}
