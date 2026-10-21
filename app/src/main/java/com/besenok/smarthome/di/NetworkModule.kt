package com.besenok.smarthome.di

import android.content.Context
import com.besenok.smarthome.data.local.datastore.AppPreferencesDataSource
import com.besenok.smarthome.data.remote.iobroker.AdapterService
import com.besenok.smarthome.data.remote.iobroker.DeviceClassifier
import com.besenok.smarthome.data.remote.iobroker.DeviceParser
import com.besenok.smarthome.data.remote.iobroker.IobrokerApiClient
import com.besenok.smarthome.data.remote.iobroker.IobrokerRestClient
import com.besenok.smarthome.data.remote.weather.WeatherApiService
import com.besenok.smarthome.data.remote.time.TimeApiService
import com.besenok.smarthome.data.repository.IobrokerRepositoryImpl
import com.besenok.smarthome.data.repository.WeatherRepositoryImpl
import com.besenok.smarthome.data.repository.TimeRepositoryImpl
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.domain.repository.WeatherRepository
import com.besenok.smarthome.domain.repository.TimeRepository
import com.besenok.smarthome.util.RateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter {
        return RateLimiter(maxRequests = 10, windowSeconds = 1)
    }

    @Provides
    @Singleton
    fun provideJson(): Json = json

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        // Осознанное решение: ioBroker в локальной сети работает с самоподписанным сертификатом
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    hostnameVerifier { hostname, _ -> true }
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Provides
    @Singleton
    fun provideAdapterService(): AdapterService = AdapterService()

    @Provides
    @Singleton
    fun provideDeviceParser(): DeviceParser = DeviceParser()

    @Provides
    @Singleton
    fun provideDeviceClassifier(): DeviceClassifier = DeviceClassifier()

    @Provides
    @Singleton
    fun provideIobrokerApiClient(
        restClient: IobrokerRestClient,
        adapterService: AdapterService,
        deviceParser: DeviceParser,
        deviceClassifier: DeviceClassifier,
        json: Json
    ): IobrokerApiClient = IobrokerApiClient(restClient, adapterService, deviceParser, deviceClassifier, json)

    @Provides
    @Singleton
    fun provideAppPreferencesDataSource(@ApplicationContext context: Context): AppPreferencesDataSource =
        AppPreferencesDataSource(context)
}
