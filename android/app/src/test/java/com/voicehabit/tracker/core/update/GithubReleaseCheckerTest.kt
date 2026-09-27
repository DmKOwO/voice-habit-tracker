package com.voicehabit.tracker.core.update.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class GithubReleaseCheckerTest {

    private fun apkAsset(url: String = "https://example.com/app.apk"): String =
        """[{"name": "app.apk", "content_type": "application/vnd.android.package-archive",
            "size": 1000, "browser_download_url": "$url"}]"""

    private fun releaseBody(tag: String, prerelease: Boolean = false): String = """
        {"tag_name": "$tag", "name": "$tag", "body": "notes", "draft": false,
         "prerelease": $prerelease, "published_at": "2026-09-20T10:00:00Z",
         "assets": ${apkAsset()}}
    """.trimIndent()

    private fun fakeApi(
        response: GithubApi.Response? = null,
        error: IOException? = null
    ) = object : GithubApi {
        override fun getLatestRelease(owner: String, repo: String): GithubApi.Response {
            error?.let { throw it }
            return response ?: error("no stub")
        }
    }

    private fun ok(body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        GithubApi.Response(code, headers, body)

    @Test
    fun newerReleaseIsOffered() {
        val checker = GithubReleaseChecker(
            fakeApi(ok(releaseBody("v2.0.0"))),
            currentVersion = { SemVer(1, 5, 0) }
        )
        val result = checker.check("owner", "repo")
        assertTrue(result is CheckResult.UpdateAvailable)
        assertEquals("2.0.0", (result as CheckResult.UpdateAvailable).release.version.toString())
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() {
        for (current in listOf("2.0.0", "2.1.0", "3.0.0")) {
            val checker = GithubReleaseChecker(
                fakeApi(ok(releaseBody("v2.0.0"))),
                currentVersion = { SemVer.parse(current) }
            )
            assertEquals(current, CheckResult.UpToDate, checker.check("o", "r"))
        }
    }

    @Test
    fun prereleaseIsIgnoredByDefaultButAcceptedOnRequest() {
        val strict = GithubReleaseChecker(
            fakeApi(ok(releaseBody("v2.0.0-beta", prerelease = true))),
            currentVersion = { SemVer(1, 0, 0) }
        )
        assertEquals(CheckResult.UpToDate, strict.check("o", "r"))

        val lenient = GithubReleaseChecker(
            fakeApi(ok(releaseBody("v2.0.0-beta", prerelease = true))),
            currentVersion = { SemVer(1, 0, 0) }
        )
        val result = lenient.check("o", "r", includePrerelease = true)
        assertTrue(result is CheckResult.UpdateAvailable)
    }

    @Test
    fun missingReleasesMapToNoReleases() {
        val checker = GithubReleaseChecker(
            fakeApi(ok("""{"message": "Not Found"}""", code = 404)),
            currentVersion = { SemVer(1, 0, 0) }
        )
        assertEquals(CheckResult.NoReleases, checker.check("o", "empty"))
    }

    @Test
    fun offlineMapsToOfflineNotError() {
        for (error in listOf(UnknownHostException("dns"), IOException("timeout"))) {
            val checker = GithubReleaseChecker(
                fakeApi(error = error),
                currentVersion = { SemVer(1, 0, 0) }
            )
            assertEquals(error.message, CheckResult.Offline, checker.check("o", "r"))
        }
    }

    @Test
    fun rateLimitReturnsRetryHintFromResetHeader() {
        val resetInFuture = System.currentTimeMillis() / 1000L + 120
        val checker = GithubReleaseChecker(
            fakeApi(
                ok(
                    """{"message": "API rate limit exceeded"}""",
                    code = 403,
                    headers = mapOf(
                        "X-RateLimit-Remaining" to "0",
                        "X-RateLimit-Reset" to resetInFuture.toString()
                    )
                )
            ),
            currentVersion = { SemVer(1, 0, 0) }
        )
        val result = checker.check("o", "r")
        assertTrue(result is CheckResult.RateLimited)
        val wait = (result as CheckResult.RateLimited).retryAfterMillis
        assertTrue("Ожидание около 120 c, получено $wait", wait != null && wait in 60_000..180_000)
    }

    @Test
    fun rateLimitWithoutHeadersStillRateLimited() {
        val checker = GithubReleaseChecker(
            fakeApi(ok("{}", code = 429)),
            currentVersion = { SemVer(1, 0, 0) }
        )
        val result = checker.check("o", "r")
        assertTrue(result is CheckResult.RateLimited)
        assertEquals(null, (result as CheckResult.RateLimited).retryAfterMillis)
    }

    @Test
    fun serverErrorsAndBrokenPayloadsAreInvalid() {
        val serverError = GithubReleaseChecker(
            fakeApi(ok("boom", code = 500)),
            currentVersion = { SemVer(1, 0, 0) }
        )
        val httpResult = serverError.check("o", "r")
        assertTrue(httpResult is CheckResult.Invalid)

        val broken = GithubReleaseChecker(
            fakeApi(ok("{oops")),
            currentVersion = { SemVer(1, 0, 0) }
        )
        assertTrue(broken.check("o", "r") is CheckResult.Invalid)
    }

    @Test
    fun unknownCurrentVersionIsReported() {
        val checker = GithubReleaseChecker(
            fakeApi(ok(releaseBody("v9.9.9"))),
            currentVersion = { null }
        )
        val result = checker.check("o", "r")
        assertTrue(result is CheckResult.Invalid)
    }
}

