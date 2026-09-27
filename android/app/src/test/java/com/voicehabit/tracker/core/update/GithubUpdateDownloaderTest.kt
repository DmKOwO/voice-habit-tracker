package com.voicehabit.tracker.core.update.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Минимальный HTTP-сервер на ServerSocket: `com.sun.net.httpserver` недоступен
 * в classpath юнит-тестов, а новая тестовая зависимость ради одного теста —
 * лишний вес. Сервер отдаёт ровно то, что нужно для граничных случаев.
 */
private class MiniHttpServer(
    private val respond: (requestHead: String) -> ByteArray,
    private val rawHeaders: (payloadSize: Long) -> String = { size ->
        "HTTP/1.1 200 OK\r\nContent-Length: $size\r\nConnection: close\r\n\r\n"
    }
) {
    private val socket = ServerSocket(0, 1)
    val port: Int get() = socket.localPort
    val url: String get() = "http://127.0.0.1:$port/app.apk"

    fun start() {
        thread(isDaemon = true, name = "mini-http") {
            try {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    try {
                        val head = buildString {
                            val reader = client.getInputStream().bufferedReader()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                append(line).append('\n')
                            }
                        }
                        val body = respond(head)
                        val out = client.getOutputStream()
                        out.write(rawHeaders(body.size.toLong()).toByteArray())
                        out.write(body)
                        out.flush()
                    } finally {
                        client.close()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stop() = runCatching { socket.close() }
}

class GithubUpdateDownloaderTest {

    @Test
    fun downloadsFileWithProgressCallbacks() = runBlocking {
        val payload = ByteArray(100_000) { (it % 251).toByte() }
        val server = MiniHttpServer(respond = { payload })
        server.start()
        try {
            val target = Files.createTempDirectory("dl").resolve("app.apk").toFile()
            val lastProgress = AtomicLong(0)
            var callbacks = 0
            var lastTotal: Long? = null
            val result = GithubUpdateDownloader().downloadToFile(
                server.url, target, expectedSizeBytes = payload.size.toLong(),
                onProgress = { downloaded, total ->
                    callbacks++
                    lastProgress.set(downloaded)
                    lastTotal = total
                }
            )

            assertTrue(result is GithubUpdateDownloader.DownloadResult.Done)
            val done = result as GithubUpdateDownloader.DownloadResult.Done
            assertEquals(payload.size.toLong(), done.bytes)
            assertTrue("Прогресс должен приходить чанками", callbacks > 1)
            assertEquals(payload.size.toLong(), lastProgress.get())
            assertEquals(payload.size.toLong(), lastTotal)
            assertTrue(target.readBytes().contentEquals(payload))
        } finally {
            server.stop()
        }
    }

    @Test
    fun emptyBodyIsRejectedAndCleanedUp() = runBlocking {
        val server = MiniHttpServer(respond = { ByteArray(0) })
        server.start()
        try {
            val target = Files.createTempDirectory("dl").resolve("empty.apk").toFile()
            val result = GithubUpdateDownloader().downloadToFile(server.url, target)
            assertTrue(result is GithubUpdateDownloader.DownloadResult.Failed)
            assertTrue("Пустой файл не должен оставаться на диске", !target.exists() || target.length() == 0L)
        } finally {
            server.stop()
        }
    }

    @Test
    fun httpErrorsAreReported() = runBlocking {
        val server = MiniHttpServer(
            respond = { ByteArray(0) },
            rawHeaders = { "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" }
        )
        server.start()
        try {
            val target = Files.createTempDirectory("dl").resolve("missing.apk").toFile()
            val result = GithubUpdateDownloader().downloadToFile(server.url, target)
            assertTrue(result is GithubUpdateDownloader.DownloadResult.Failed)
            assertTrue((result as GithubUpdateDownloader.DownloadResult.Failed).reason.contains("404"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun oversizedFilesAreRefused() = runBlocking {
        // Сервер врёт в Content-Length: файл якобы больше лимита.
        // Защита должна сработать до чтения тела.
        val server = MiniHttpServer(
            respond = { ByteArray(16) },
            rawHeaders = {
                "HTTP/1.1 200 OK\r\nContent-Length: ${GithubUpdateDownloader.MAX_APK_BYTES + 1}\r\nConnection: close\r\n\r\n"
            }
        )
        server.start()
        try {
            val target = Files.createTempDirectory("dl").resolve("huge.apk").toFile()
            val result = GithubUpdateDownloader().downloadToFile(server.url, target)
            assertTrue(result is GithubUpdateDownloader.DownloadResult.Failed)
            assertTrue((result as GithubUpdateDownloader.DownloadResult.Failed).reason.contains("500"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun unreachableHostFailsFast() = runBlocking {
        val target = Files.createTempDirectory("dl").resolve("missing.apk").toFile()
        val result = GithubUpdateDownloader().downloadToFile(
            "http://127.0.0.1:1/unreachable.apk",
            target
        )
        assertTrue(result is GithubUpdateDownloader.DownloadResult.Failed)
        assertTrue(!target.exists())
    }
}
