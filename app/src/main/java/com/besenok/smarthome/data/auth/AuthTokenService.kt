package com.besenok.smarthome.data.auth

import android.util.Log
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.util.CryptoHelper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

private const val TAG = "AuthTokenService"

@Serializable
data class AuthToken(
    val token: String,
    @SerialName("expire") val expire: Long,
    @SerialName("created") val created: Long
) {
    val isValid: Boolean
        get() = System.currentTimeMillis() < expire * 1000L

    val remainingSeconds: Long
        get() = maxOf(0, (expire * 1000L - System.currentTimeMillis()) / 1000)
}

@Singleton
class AuthTokenService @Inject constructor(
    private val preferences: AppPreferencesDataSource,
    private val json: Json
) {
    private var currentToken: AuthToken? = null
    private var httpClient: HttpClient? = null

    suspend fun authenticate(): Result<AuthToken> {
        return try {
            val host = preferences.iobrokerHost.first()
            val port = preferences.iobrokerPort.first()
            val username = preferences.iobrokerUsername.first()
            val password = CryptoHelper.decrypt(preferences.iobrokerPassword.first())

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

            val response = client.post("https://$host:$port/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    AuthRequest.serializer(),
                    AuthRequest(username, password)
                ))
            }

            if (response.status.isSuccess()) {
                val body = response.body<String>()
                val tokenData = json.decodeFromString<AuthTokenResponse>(body)

                if (tokenData.token != null) {
                    currentToken = AuthToken(
                        token = tokenData.token!!,
                        expire = tokenData.expire ?: (System.currentTimeMillis() / 1000 + 3600),
                        created = System.currentTimeMillis() / 1000
                    )

                    Log.d(TAG, "Authenticated successfully, token expires in ${currentToken!!.remainingSeconds}s")
                    Result.success(currentToken!!)
                } else {
                    Log.e(TAG, "No token in response")
                    Result.failure(Exception("No token received"))
                }
            } else {
                val errorBody = response.body<String>()
                Log.e(TAG, "Auth failed: ${response.status} - $errorBody")
                Result.failure(Exception("Auth failed: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            Result.failure(e)
        }
    }

    suspend fun getValidToken(): Result<String> {
        if (currentToken != null && currentToken!!.isValid) {
            Log.d(TAG, "Using cached token (${currentToken!!.remainingSeconds}s remaining)")
            return Result.success(currentToken!!.token)
        }

        Log.d(TAG, "Token expired or not found, re-authenticating...")
        return authenticate().mapCatching { token ->
            token.token
        }
    }

    suspend fun getAuthHeader(): Result<Pair<String, String>> {
        return getValidToken().map { token ->
            HttpHeaders.Authorization to "Bearer $token"
        }
    }

    suspend fun getAuthorizationHeader(): Result<String> {
        return getValidToken().map { token ->
            "Bearer $token"
        }
    }

    suspend fun refreshToken(): Result<AuthToken> {
        Log.d(TAG, "Force refreshing token...")
        currentToken = null
        return authenticate()
    }

    fun disconnect() {
        currentToken = null
        httpClient?.close()
        httpClient = null
        Log.d(TAG, "Disconnected, token cleared")
    }

    fun hasValidToken(): Boolean {
        return currentToken != null && currentToken!!.isValid
    }

    @Serializable
    data class AuthRequest(
        val user: String,
        val pass: String
    )

    @Serializable
    data class AuthTokenResponse(
        val token: String?,
        val expire: Long?,
        val message: String?
    )
}
