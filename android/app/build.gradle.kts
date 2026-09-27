plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

import java.io.FileInputStream
import java.util.Properties

/**
 * Ключ ИИ для встраивания в сборку: -Pсвойство → local.properties → env.
 * local.properties не коммитится (.gitignore), поэтому ключи переживают
 * любые пересборки на этой машине без повторного ввода.
 * Экранируется под Java-строку, чтобы ключ с кавычками не ломал компиляцию.
 */
fun bundledKey(propName: String, envName: String, localPropName: String): String {
    var fromLocal: String? = null
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        runCatching {
            val props = Properties()
            FileInputStream(localFile).use { props.load(it) }
            fromLocal = props.getProperty(localPropName)
        }
    }
    val raw = (project.findProperty(propName) as String?)
        ?: fromLocal
        ?: System.getenv(envName)
        ?: ""
    return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
}

/**
 * Версия из version.properties (единый источник для локальных сборок).
 * CI перекрывает её -PversionName/-PversionCode из тега.
 */
fun appVersion(propName: String, fallback: String): String {
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        runCatching {
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            props.getProperty(propName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return (project.findProperty(propName) as String?) ?: fallback
}

android {
    namespace = "com.voicehabit.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voicehabit.tracker"
        minSdk = 26
        targetSdk = 35
        // Версия: version.properties — единый источник для локальных сборок
        // (release.sh поднимает её автоматически). CI перекрывает значения
        // -PversionName/-PversionCode из тега. Это критично для OTA:
        // проверка GitHub сравнивает versionName, а установщик — versionCode.
        versionCode = appVersion("VERSION_CODE", "1").toIntOrNull() ?: 1
        versionName = appVersion("VERSION_NAME", "1.0.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Встроенные ключи ИИ: CI подставляет GROQ_API_KEY/GEMINI_API_KEY из секретов.
        // Приложение работает из коробки, ручной ввод в настройках перекрывает их.
        // Пусто по умолчанию — тогда работает офлайн-парсер и свой бэкенд.
        buildConfigField("String", "BUNDLED_GROQ_API_KEY", "\"${bundledKey("bundledGroqKey", "GROQ_API_KEY", "groq.api.key")}\"")
        buildConfigField("String", "BUNDLED_GEMINI_API_KEY", "\"${bundledKey("bundledGeminiKey", "GEMINI_API_KEY", "gemini.api.key")}\"")
    }

    sourceSets {
        // Схемы Room обязаны попадать в репозиторий: без них нельзя проверить миграции.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    signingConfigs {
        // OTA-подпись: CI кладёт keystore.jks из секрета KEYSTORE_BASE64.
        // Без файла сборка молча падает на debug-подпись (для ручных сборок).
        // Важно: на телефон ставьте APK из GitHub Release — только тогда
        // сертификат совпадёт и следующие OTA встанут без переустановки.
        create("ota") {
            val ksFile = file("keystore.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
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
            signingConfig = if (file("keystore.jks").exists()) {
                signingConfigs.getByName("ota")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Схемы Room обязаны попадать в репозиторий: без них миграции нельзя проверить,
// а следующая версия БД молча удалила бы данные пользователя.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Coroutines & WorkManager
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)


    // Baseline profile: ускоряет холодный старт на реальных устройствах (ART + R8)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.appcompat)

    // F11 Health Connect: шаги/сон как автологи привычек
    implementation(libs.health.connect.client)

    // F1 Vosk: офлайн-распознавание речи на устройстве
    implementation(libs.vosk.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
