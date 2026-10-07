package org.every.nook.api.infrastructure.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.every.nook.api.application.post.error.PostMediaStorageTimeoutException
import org.every.nook.api.domain.post.PostMedia
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Timeout(10)
class JdkRemoteMediaHttpClientTest {
    @Test
    fun `stalled video body times out cleans partial file and allows next download`() {
        ServerFixture().use { fixture ->
            val before = temporaryDownloads()
            val started = System.nanoTime()

            assertFailsWith<PostMediaStorageTimeoutException> {
                fixture.download("/stalled")
            }

            assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
            assertEquals(before, temporaryDownloads(), "Timed out partial files must be deleted")
            fixture.download("/complete").use { media ->
                assertContentEquals(byteArrayOf(1, 2), Files.readAllBytes(media.path))
            }
        }
    }

    @Test
    fun `body with no bytes is also bounded`() {
        ServerFixture().use { fixture ->
            assertFailsWith<PostMediaStorageTimeoutException> {
                fixture.download("/empty")
            }
        }
    }

    @Test
    fun `continuous slow bytes cannot extend the download deadline`() {
        ServerFixture().use { fixture ->
            assertFailsWith<PostMediaStorageTimeoutException> {
                fixture.download("/trickle")
            }
        }
    }

    @Test
    fun `completed response remains readable after its deadline`() {
        ServerFixture().use { fixture ->
            fixture.download("/complete").use { media ->
                assertTrue(fixture.release.await(WAIT_MILLIS, TimeUnit.MILLISECONDS).not())
                assertContentEquals(byteArrayOf(1, 2), Files.readAllBytes(media.path))
            }
        }
    }

    private fun temporaryDownloads(): Set<Path> = Files.list(Path.of(System.getProperty("java.io.tmpdir"))).use {
        it.filter { path -> path.fileName.toString().startsWith("nook-media-") }.toList().toSet()
    }

    private class ServerFixture : AutoCloseable {
        val release = CountDownLatch(1)
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@ServerFixture.executor
            createContext("/") { exchange -> respond(exchange) }
            start()
        }
        private val client = HttpClient.newHttpClient()
        private val properties = MediaStorageProperties(readTimeout = Duration.ofMillis(DEADLINE_MILLIS))
        private val remote = JdkRemoteMediaHttpClient(client, properties)

        fun download(path: String): DownloadedMedia {
            val downloader = JdkRemoteMediaDownloader(
                RemoteMediaHttpClient { remote.get(URI("http://127.0.0.1:${server.address.port}$path")) },
                properties,
                PublicHttpsUriValidator(),
            )
            return downloader.download("https://1.1.1.1/video.mp4", PostMedia.MediaType.VIDEO)
        }

        private fun respond(exchange: HttpExchange) {
            exchange.use {
                it.responseHeaders.add("Content-Type", "video/mp4")
                it.sendResponseHeaders(200, 0)
                when (it.requestURI.path) {
                    "/complete" -> it.responseBody.write(byteArrayOf(1, 2))

                    "/empty" -> release.await(WAIT_SECONDS, TimeUnit.SECONDS)

                    "/trickle" -> {
                        while (!release.await(TRICKLE_MILLIS, TimeUnit.MILLISECONDS)) {
                            it.responseBody.write(1)
                            it.responseBody.flush()
                        }
                    }

                    else -> {
                        it.responseBody.write(1)
                        it.responseBody.flush()
                        release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    }
                }
            }
        }

        override fun close() {
            release.countDown()
            server.stop(0)
            client.close()
            executor.close()
        }
    }

    private companion object {
        const val DEADLINE_MILLIS = 500L
        const val TRICKLE_MILLIS = 50L
        const val WAIT_MILLIS = 700L
        const val WAIT_SECONDS = 5L
    }
}
