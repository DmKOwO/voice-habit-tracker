package com.voicehabit.tracker.core.update.github

import com.google.gson.JsonParser

/**
 * Релиз GitHub, как его отдаёт `GET /repos/{owner}/{repo}/releases/latest`.
 *
 * Парсинг намеренно терпимый: GitHub иногда меняет схему, а падение разбора
 * не должно ронять проверку обновлений — возвращается null с причиной.
 */
data class GithubRelease(
    val tagName: String,
    val version: SemVer,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long?,
    val publishedAtIso: String?,
    val prerelease: Boolean
) {
    companion object {
        fun parseTagOnly(tagName: String): GithubRelease? {
            val version = SemVer.parse(tagName) ?: return null
            return GithubRelease(
                tagName = tagName,
                version = version,
                name = tagName,
                notes = "",
                apkUrl = "",
                apkSizeBytes = null,
                publishedAtIso = null,
                prerelease = false
            )
        }
    }
}

sealed interface ReleaseParseResult {
    data class Ok(val release: GithubRelease) : ReleaseParseResult
    /** draft-релизы и чужие теги пропускаем молча — это не ошибка. */
    data object Skipped : ReleaseParseResult
    data class Invalid(val reason: String) : ReleaseParseResult
}

object GithubReleaseParser {

    fun parse(body: String): ReleaseParseResult {
        val root = try {
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            return ReleaseParseResult.Invalid("Некорректный JSON релиза: ${e.message}")
        }

        if (root.get("draft")?.asBoolean == true) return ReleaseParseResult.Skipped

        val tagName = root.get("tag_name")?.takeIf { !it.isJsonNull }?.asString
            ?: return ReleaseParseResult.Invalid("В ответе нет tag_name")
        val version = SemVer.parse(tagName)
            ?: return ReleaseParseResult.Invalid("Тег '$tagName' не является SemVer")
        val prerelease = root.get("prerelease")?.asBoolean ?: false

        val assets = root.getAsJsonArray("assets")
            ?: return ReleaseParseResult.Invalid("В ответе нет списка assets")
        val apk = assets.asSequence()
            .mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
            .firstOrNull { element ->
                val name = element.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val contentType = element.get("content_type")?.takeIf { !it.isJsonNull }?.asString ?: ""
                name.endsWith(".apk", ignoreCase = true) ||
                    contentType == "application/vnd.android.package-archive"
            } ?: return ReleaseParseResult.Invalid("К релизу $tagName не прикреплён APK")

        val apkUrl = apk.get("browser_download_url")?.takeIf { !it.isJsonNull }?.asString
            ?: return ReleaseParseResult.Invalid("У APK-ассета нет browser_download_url")

        return ReleaseParseResult.Ok(
            GithubRelease(
                tagName = tagName,
                version = version,
                name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { tagName } ?: tagName,
                notes = root.get("body")?.takeIf { !it.isJsonNull }?.asString ?: "",
                apkUrl = apkUrl,
                apkSizeBytes = apk.get("size")?.takeIf { !it.isJsonNull }?.asLong,
                publishedAtIso = root.get("published_at")?.takeIf { !it.isJsonNull }?.asString,
                prerelease = prerelease
            )
        )
    }
}
