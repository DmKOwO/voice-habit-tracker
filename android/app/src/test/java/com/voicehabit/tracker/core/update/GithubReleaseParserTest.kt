package com.voicehabit.tracker.core.update.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseParserTest {

    private fun releaseJson(
        tag: String = "v1.2.0",
        name: String = "Voice Habit Tracker v1.2.0",
        body: String = "### Что нового\r\n- задачи\r\n- виджеты",
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: String = """
            [{
              "name": "voice-habit-tracker-v1.2.0.apk",
              "content_type": "application/vnd.android.package-archive",
              "size": 20971520,
              "browser_download_url": "https://github.com/o/r/releases/download/v1.2.0/app.apk"
            }]
        """.trimIndent()
    ): String = """
        {
          "tag_name": "$tag",
          "name": "$name",
          "body": ${body.toJsonString()},
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-09-20T10:00:00Z",
          "assets": $assets
        }
    """.trimIndent()

    private fun String.toJsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + "\""

    @Test
    fun parsesRealWorldReleasePayload() {
        val result = GithubReleaseParser.parse(releaseJson())

        assertTrue(result is ReleaseParseResult.Ok)
        val release = (result as ReleaseParseResult.Ok).release
        assertEquals("v1.2.0", release.tagName)
        assertEquals(SemVer(1, 2, 0), release.version)
        assertEquals("Voice Habit Tracker v1.2.0", release.name)
        assertTrue(release.notes.contains("задачи"))
        assertEquals("https://github.com/o/r/releases/download/v1.2.0/app.apk", release.apkUrl)
        assertEquals(20971520L, release.apkSizeBytes)
        assertEquals("2026-09-20T10:00:00Z", release.publishedAtIso)
    }

    @Test
    fun skipsDraftReleasesSilently() {
        val result = GithubReleaseParser.parse(releaseJson(draft = true))
        assertTrue(result is ReleaseParseResult.Skipped)
    }

    @Test
    fun rejectsReleaseWithoutApk() {
        val noApk = releaseJson(
            assets = """[{"name": "notes.txt", "content_type": "text/plain", "size": 10,
                "browser_download_url": "https://example.com/notes.txt"}]"""
        )
        val result = GithubReleaseParser.parse(noApk)
        assertTrue(result is ReleaseParseResult.Invalid)
        assertTrue((result as ReleaseParseResult.Invalid).reason.contains("APK"))
    }

    @Test
    fun rejectsNonSemVerTag() {
        val result = GithubReleaseParser.parse(releaseJson(tag = "nightly-2026"))
        assertTrue(result is ReleaseParseResult.Invalid)
    }

    @Test
    fun rejectsBrokenJsonAndMissingFields() {
        assertTrue(GithubReleaseParser.parse("{oops") is ReleaseParseResult.Invalid)
        assertTrue(GithubReleaseParser.parse("{}") is ReleaseParseResult.Invalid)
        assertTrue(GithubReleaseParser.parse("""{"tag_name": "v1.0.0"}""") is ReleaseParseResult.Invalid)
    }

    @Test
    fun picksApkAmongMixedAssets() {
        val mixed = releaseJson(
            assets = """[
              {"name": "app.apk.sha256", "content_type": "text/plain", "size": 70,
               "browser_download_url": "https://example.com/s"},
              {"name": "voice-habit-tracker-v2.0.0.apk", "content_type": "application/octet-stream",
               "size": 1, "browser_download_url": "https://example.com/app.apk"}
            ]"""
        )
        val result = GithubReleaseParser.parse(mixed)
        assertTrue(result is ReleaseParseResult.Ok)
        assertEquals("https://example.com/app.apk", (result as ReleaseParseResult.Ok).release.apkUrl)
    }

    @Test
    fun flagsPrerelease() {
        val result = GithubReleaseParser.parse(releaseJson(tag = "v2.0.0-beta.1", prerelease = true))
        assertTrue(result is ReleaseParseResult.Ok)
        assertEquals(true, (result as ReleaseParseResult.Ok).release.prerelease)
    }
}
