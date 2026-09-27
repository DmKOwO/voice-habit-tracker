package com.voicehabit.tracker.core.network

import java.net.URI
import java.util.Locale

/**
 * Политика сетевых адресов приложения.
 *
 * В release-сборке cleartext-трафик запрещён через res/xml/network_security_config.xml,
 * поэтому адрес проверяется до попытки вызова: HTTPS принимается всегда, HTTP — только
 * для локальной схемы разработки (10.0.2.2 — хост-машина из эмулятора, localhost/127.0.0.1/::1).
 * Так приложение не падает с IllegalArgumentException от OkHttp/Retrofit, а спокойно
 * уходит в офлайн-ветку обработки голоса.
 */
object NetworkEndpointPolicy {

    private val LOCAL_DEVELOPMENT_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1", "::1")

    sealed interface Endpoint {
        /** Нормализованный base URL (всегда со схемой, хостом и завершающим слэшем). */
        data class Allowed(val baseUrl: String) : Endpoint
        data class Rejected(val reason: String) : Endpoint
    }

    fun evaluate(rawBaseUrl: String?): Endpoint {
        val raw = rawBaseUrl?.trim().orEmpty()
        if (raw.isEmpty()) {
            return Endpoint.Rejected("URL бэкенда не задан")
        }
        if (!raw.contains("://")) {
            return Endpoint.Rejected("URL должен начинаться с https://")
        }

        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return Endpoint.Rejected("Некорректный URL: $raw")

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)?.trim('[', ']')
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return Endpoint.Rejected("В URL не указан корректный хост")
        }
        if (uri.userInfo != null) {
            return Endpoint.Rejected("URL с логином и паролем не поддерживается")
        }

        val isHttps = scheme == "https"
        val isLocalHttp = scheme == "http" && host in LOCAL_DEVELOPMENT_HOSTS
        if (!isHttps && !isLocalHttp) {
            return Endpoint.Rejected(
                "Cleartext HTTP разрешён только для локальной схемы разработки " +
                    "(${LOCAL_DEVELOPMENT_HOSTS.joinToString()})"
            )
        }

        val path = uri.rawPath.orEmpty().trimEnd('/')
        return Endpoint.Allowed("$scheme://${uri.rawAuthority}$path/")
    }

    /** Удобная обёртка: адрес пригоден к использованию. */
    fun isAllowed(rawBaseUrl: String?): Boolean = evaluate(rawBaseUrl) is Endpoint.Allowed
}
