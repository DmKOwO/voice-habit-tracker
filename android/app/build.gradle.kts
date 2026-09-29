plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

import java.io.FileInputStream
import java.util.Properties

/**
 * Ключ ИИ для встраивания в сборку.
 *
 * БЕЗОПАСНОСТЬ: по умолчанию ключи НЕ попадают ни в какую сборку.
 * Встраивание включается только явным флагом `-PbundleAiKeys=true`
 * (и никогда — через переменную окружения, чтобы флаг нельзя было
 * случайно включить на CI одной строкой в secrets).
 *
 * Почему так: строка в BuildConfig попадает в classes.dex в виде обычной
 * константы и достаётся из публичного APK через `strings` за секунду.
 * Ключ автора в APK каждого пользователя — это его квота и его счёт.
 *
 * Что делать вместо этого: пользователь вводит свой ключ в Настройках
 * (хранится в приватном SharedPreferences), либо приложение работает
 * полностью офлайн на встроенном парсере — он не требует ни сети,
 * ни ключей, ни моделей.
 *
 * Источники при включённом флаге: -Pсвойство → local.properties → env.
 * Экранируется под Java-строку, чтобы ключ с кавычками не ломал компиляцию.
 */
fun bundledKey(propName: String, envName: String, localPropName: String): String {
    val enabled = (project.findProperty("bundleAiKeys") as String?)?.toBoolean() == true
    if (!enabled) {
        return ""
    }
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
    if (raw.isNotBlank()) {
        logger.warn(
            "ВНИМАНИЕ: ключ ИИ встраивается в APK. Такой ключ публично извлекаем. " +
                "Для обычной сборки оставьте -PbundleAiKeys выключенным."
        )
    }
    return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
}

/**
 * Версия из version.properties (единый источник для локальных сборок).
 * CI перекрывает её -PversionName/-PversionCode из тега.
 */
fun appVersion(propName: String, fallback: String): String {
    // 1. Приоритет аргументов командной строки (-PversionName=... или -PversionCode=...)
    val cliProp = (project.findProperty(propName) as? String)
        ?: (project.findProperty(propName.lowercase()) as? String)
        ?: if (propName == "VERSION_NAME") (project.findProperty("versionName") as? String) else null
        ?: if (propName == "VERSION_CODE") (project.findProperty("versionCode") as? String) else null
    if (!cliProp.isNullOrBlank()) {
        return cliProp.trim()
    }

    // 2. Чтение из version.properties
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        runCatching {
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            props.getProperty(propName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return fallback
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

        // Встроенные ключи ИИ: по умолчанию ПУСТЫЕ (см. bundledKey выше).
        // Чтобы встроить свои ключи в личную сборку: ./gradlew assembleRelease -PbundleAiKeys=true
        // Приложение полностью работоспособно без них — офлайн-парсер не требует ключей.
        buildConfigField("String", "BUNDLED_GROQ_API_KEY", "\"${bundledKey("bundledGroqKey", "GROQ_API_KEY", "groq.api.key")}\"")
        buildConfigField("String", "BUNDLED_GEMINI_API_KEY", "\"${bundledKey("bundledGeminiKey", "GEMINI_API_KEY", "gemini.api.key")}\"")
    }

    sourceSets {
        // Схемы Room обязаны попадать в репозиторий: без них нельзя проверить миграции.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    signingConfigs {
        // Постоянная подпись для всех сборок: релизы, OTA и локальный debug.
        // Это навсегда исключает ошибку «пакет конфликтует с существующим пакетом».
        //
        // БЕЗОПАСНОСТЬ: пароль НЕ имеет фолбэка. Раньше здесь стоял литерал
        // "dairy-release-key", а сам release.jks лежал в публичном репозитории —
        // то есть подпись релизов была общедоступной. Теперь ключ берётся только
        // из переменных окружения или локального keystore.properties (в git не коммитится).
        create("appSigning") {
            val propsFile = file("keystore/keystore.properties")
            val props = Properties()
            if (propsFile.exists()) {
                runCatching { FileInputStream(propsFile).use { props.load(it) } }
            }
            val customKs = file(props.getProperty("storeFile") ?: "keystore.jks")

            storeFile = if (customKs.exists()) customKs else file("keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: props.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: props.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: props.getProperty("keyPassword")

            if (storePassword == null || keyAlias == null || keyPassword == null) {
                throw GradleException(
                    "Нет параметров подписи. Задайте KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD " +
                        "в переменных окружения или создайте android/app/keystore/keystore.properties " +
                        "(образец — keystore.properties.example)."
                )
            }

            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            // Подписываем debug тем же постоянным ключом, чтобы локальные сборки (adb install)
            // никогда не конфликтовали с релизными сборками из GitHub Releases / OTA.
            signingConfig = signingConfigs.getByName("appSigning")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("appSigning")
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
    implementation(libs.androidx.documentfile)
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
