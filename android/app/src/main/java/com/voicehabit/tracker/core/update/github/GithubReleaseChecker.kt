package com.voicehabit.tracker.core.update.github

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Проверка обновлений через GitHub REST API без авторизации.
 *
 * Граничные случаи (все покрыты тестами):
 * - нет интернета ([UnknownHostException], таймауты) → [CheckResult.Offline];
 * - репозиторий пуст / релизов нет (HTTP 404) → [CheckResult.NoReleases];
 * - rate-limit анонимных запросов, 60/час (HTTP 403/429 + `X-RateLimit-Remaining: 0`)
 *   → [CheckResult.RateLimited] с временем сброса из `X-RateLimit-Reset`;
 * - draft без APK, мусор в JSON → [CheckResult.Invalid];
 * - текущая версия >= версии релиза → [CheckResult.UpToDate].
 *
 * Слой [GithubApi] отделён от HTTP, чтобы checker тестировался без сети.
 */
sealed interface CheckResult {
    data class UpdateAvailable(val release: GithubRelease) : CheckResult
    data object UpToDate : CheckResult
    data object NoReleases : CheckResult
    data object Offline : CheckResult
    data class RateLimited(val retryAfterMillis: Long?) : CheckResult
    data class Invalid(val reason: String) : CheckResult
}

interface GithubApi {
    data class Response(val code: Int, val headers: Map<String, String>, val body: String)

    @Throws(IOException::class)
    fun getLatestRelease(owner: String, repo: String): Response
}

class OkHttpGithubApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) : GithubApi {
    override fun getLatestRelease(owner: String, repo: String): GithubApi.Response {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "voice-habit-tracker")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val headers = response.headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }
            return GithubApi.Response(
                code = response.code,
                headers = headers,
                body = response.body?.string() ?: ""
            )
        }
    }
}

class GithubReleaseChecker(
    private val api: GithubApi = OkHttpGithubApi(),
    private val currentVersion: () -> SemVer?
) {
    fun check(owner: String, repo: String, includePrerelease: Boolean = false): CheckResult {
        val current = currentVersion()
            ?: return CheckResult.Invalid("Не удалось определить текущую версию приложения")
        val response = try {
            api.getLatestRelease(owner.trim(), repo.trim())
        } catch (e: UnknownHostException) {
            return CheckResult.Offline
        } catch (e: IOException) {
            // Таймауты и обрывы соединения — тоже «оффлайн», а не «ошибка»:
            // пользователю показываем спокойный статус, а не красный экран.
            return CheckResult.Offline
        }

        if (response.code == 404) return CheckResult.NoReleases

        if ((response.code == 403 || response.code == 429) && isRateLimited(response)) {
            return CheckResult.RateLimited(retryAfterMillis(response))
        }
        if (response.code != 200) {
            return CheckResult.Invalid("GitHub вернул HTTP ${response.code}")
        }

        return when (val parsed = GithubReleaseParser.parse(response.body)) {
            is ReleaseParseResult.Skipped -> CheckResult.NoReleases
            is ReleaseParseResult.Invalid -> CheckResult.Invalid(parsed.reason)
            is ReleaseParseResult.Ok -> {
                val release = parsed.release
                if (release.prerelease && !includePrerelease) {
                    CheckResult.UpToDate
                } else if (release.version > current) {
                    CheckResult.UpdateAvailable(release)
                } else {
                    CheckResult.UpToDate
                }
            }
        }
    }

    private fun isRateLimited(response: GithubApi.Response): Boolean {
        val remaining = response.headers.entries
            .firstOrNull { it.key.equals("X-RateLimit-Remaining", ignoreCase = true) }
            ?.value?.trim()
        return remaining == "0" || response.code == 429
    }

    private fun retryAfterMillis(response: GithubApi.Response): Long? {
        val headers = response.headers
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        header("Retry-After")?.toLongOrNull()?.let { return it * 1000L }
        header("X-RateLimit-Reset")?.toLongOrNull()?.let { resetEpochSeconds ->
            val waitSeconds = resetEpochSeconds - System.currentTimeMillis() / 1000L
            if (waitSeconds > 0) return waitSeconds * 1000L
        }
        return null
    }
}
