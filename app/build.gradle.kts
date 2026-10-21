import java.util.Properties

// Секреты берутся из local.properties (не коммитится) или переменных окружения
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String =
    (localProps.getProperty(key) ?: System.getenv(env) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.besenok.smarthome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.besenok.smarthome"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties: openweathermap.api.key=..., iobroker.default.host=...
        buildConfigField("String", "OPENWEATHERMAP_API_KEY", "\"${secret("openweathermap.api.key", "OPENWEATHERMAP_API_KEY")}\"")
        buildConfigField("String", "IOBROKER_DEFAULT_HOST", "\"${secret("iobroker.default.host", "IOBROKER_DEFAULT_HOST")}\"")

        // Оптимизация для Raspberry Pi 5 (ARM64)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Оптимизация сборки для EmteriaOS на Pi 5
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
}
