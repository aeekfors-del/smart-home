package com.besenok.smarthome.data.remote.iobroker

import android.util.Log
import com.besenok.smarthome.data.auth.AuthTokenService
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.domain.repository.ConnectionState
import com.besenok.smarthome.util.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

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
    private val authTokenService: AuthTokenService,
    private val json: Json
) {

    private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var reconnectJob: Job? = null
    private var currentSession: String? = null
    private var httpClient: HttpClient? = null
    private var isLoggedIn = false
    
    // Rate limiter: 10 запросов в секунду
    private val rateLimiter = RateLimiter(maxRequests = 10, windowSeconds = 1)

    suspend fun connect() {
        if (_connectionState.value is ConnectionState.Connected) return

        _connectionState.value = ConnectionState.Connecting

        try {
            val host = preferences.iobrokerHost.first()
            val port = preferences.iobrokerPort.first()

            if (host.isBlank()) {
                _connectionState.value = ConnectionState.Error("Укажите адрес ioBroker в настройках")
                return
            }

            val restPort = "8087"

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
                        hostnameVerifier { _, _ -> true }
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

            // Аутентификация через токен
            try {
                val authHeader = authTokenService.getAuthorizationHeader()
                if (authHeader.isSuccess) {
                    val token = authHeader.getOrThrow()
                    val response = client.get("https://$host:$restPort/json/getPlainValue/system.adapter.admin.0.alive") {
                        header(HttpHeaders.Authorization, token)
                        header(HttpHeaders.ContentType, "application/json")
                        header(HttpHeaders.Accept, "application/json")
                    }
                    
                    if (response.status.value in 200..299) {
                        isLoggedIn = true
                        _connectionState.value = ConnectionState.Connected("Connected to $host:$restPort")
                        startKeepAlive()
                    }
                } else {
                    _connectionState.value = ConnectionState.Error("Аутентификация не удалась: ${authHeader.exceptionOrNull()?.message}")
                    startReconnection()
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
                    
                    val authHeader = authTokenService.getAuthorizationHeader()
                    if (authHeader.isSuccess) {
                        try {
                            val token = authHeader.getOrThrow()
                            client.get("https://$host:$restPort/json/getPlainValue/system.adapter.admin.0.alive") {
                                header(HttpHeaders.Authorization, token)
                                header(HttpHeaders.ContentType, "application/json")
                                header(HttpHeaders.Accept, "application/json")
                            }
                        } catch (e: Exception) {
                            _connectionState.value = ConnectionState.Error("Потеряно соединение")
                            startReconnection()
                            break
                        }
                    } else {
                        val refreshResult = authTokenService.refreshToken()
                        if (refreshResult.isSuccess) {
                            Log.d(TAG, "Token refreshed during keep-alive")
                        } else {
                            _connectionState.value = ConnectionState.Error("Ошибка обновления токена")
                            startReconnection()
                            break
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error("Ошибка keep-alive: ${e.message}")
                    startReconnection()
                    break
                }
            }
        }
    }

    suspend fun getState(objectId: String): IobrokerState? {
        return try {
            rateLimiter.acquire()
            
            val client = httpClient ?: throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for getState")
                return null
            }
            
            val token = authHeader.getOrNull() ?: return null
            
            val plainValue: String = client.get("https://$host:$restPort/json/getPlainValue/$objectId") {
                header(HttpHeaders.Authorization, token)
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

    suspend fun setState(objectId: String, value: Any) {
        try {
            rateLimiter.acquire()
            
            val client = httpClient ?: throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for setState")
                throw Exception("No valid auth token")
            }
            
            val token = authHeader.getOrNull() ?: throw Exception("No valid auth token")
            
            val valueStr = when (value) {
                is Boolean -> if (value) "true" else "false"
                is String -> "\"$value\""
                else -> value.toString()
            }

            val encodedObjectId = java.net.URLEncoder.encode(objectId, "UTF-8")
            val url = "https://$host:$restPort/json/set/$encodedObjectId?value=$valueStr"
            
            Log.d(TAG, "setState REST URL: $url")
            val response = client.get(url) {
                header(HttpHeaders.Authorization, token)
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
    
    suspend fun setStateWebSocket(objectId: String, value: Any, ack: Boolean = true) {
        try {
            rateLimiter.acquire()
            
            val client = httpClient ?: throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for setStateWebSocket")
                throw Exception("No valid auth token")
            }
            
            val token = authHeader.getOrNull() ?: throw Exception("No valid auth token")
            
            val valueJson = when (value) {
                is Boolean -> if (value) "true" else "false"
                is String -> "\"$value\""
                else -> value.toString()
            }
            
            val encodedObjectId = java.net.URLEncoder.encode(objectId, "UTF-8")
            val url = "https://$host:$restPort/set/$encodedObjectId?value=$valueJson&ack=$ack"
            
            Log.d(TAG, "setState WebSocket URL: $url")
            val response = client.get(url) {
                header(HttpHeaders.Authorization, token)
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

    suspend fun getObject(objectId: String): JsonObject? {
        return try {
            rateLimiter.acquire()
            
            val client = httpClient ?: throw IllegalStateException("Not connected")
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for getObject")
                return null
            }
            
            val token = authHeader.getOrNull() ?: return null
            
            val response: JsonObject = client.get("https://$host:$restPort/json/get/$objectId") {
                header(HttpHeaders.Authorization, token)
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
    
    suspend fun getObjects(pattern: String = "*", generate: Boolean = false): JsonObject? {
        Log.d("IobrokerWebSocketClient", "getObjects called with pattern: $pattern")
        
        return try {
            rateLimiter.acquire()
            
            val client = httpClient
            if (client == null) {
                Log.w("IobrokerWebSocketClient", "httpClient is null, cannot make REST API call")
                return null
            }
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for getObjects")
                return null
            }
            
            val token = authHeader.getOrNull() ?: return null
            
            val response: JsonObject = client.get("https://$host:$restPort/objects?pattern=$pattern") {
                header(HttpHeaders.Authorization, token)
                header(HttpHeaders.Accept, "application/json")
            }.body()
            
            Log.d("IobrokerWebSocketClient", "Raw response keys: ${response.keys.take(10)}")
            
            response
        } catch (e: Exception) {
            Log.e("IobrokerWebSocketClient", "Error getting objects", e)
            null
        }
    }

    suspend fun getStates(pattern: String = "*"): JsonObject? {
        Log.d("IobrokerWebSocketClient", "getStates called with pattern: $pattern")
        
        return try {
            rateLimiter.acquire()
            
            val client = httpClient
            if (client == null) {
                Log.w("IobrokerWebSocketClient", "httpClient is null, cannot make REST API call")
                return null
            }
            val host = preferences.iobrokerHost.first()
            val restPort = "8087"
            
            val authHeader = authTokenService.getAuthorizationHeader()
            if (authHeader.isFailure) {
                Log.e(TAG, "No valid auth token for getStates")
                return null
            }
            
            val token = authHeader.getOrNull() ?: return null
            
            val response: JsonObject = client.get("https://$host:$restPort/states?pattern=$pattern") {
                header(HttpHeaders.Authorization, token)
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
        authTokenService.disconnect()
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
