package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import org.every.nook.api.domain.post.PostMedia
import java.io.ByteArrayInputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JdkRemoteMediaDownloaderTest {
    @Test
    fun `downloads validated image and normalizes content type`() {
        val fixture = fixture(
            response(
                status = 200,
                contentType = "image/jpeg; charset=binary",
                body = "image",
            ),
        )

        fixture.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE).use { downloaded ->
            assertEquals("image/jpeg", downloaded.contentType)
            assertEquals("jpg", downloaded.extension)
            assertContentEquals("image".toByteArray(), downloaded.path.toFile().readBytes())
        }
    }

    @Test
    fun `follows a validated HTTPS redirect`() {
        val fixture = fixture(
            response(status = 302, headers = mapOf("Location" to listOf(REDIRECT_IMAGE_URL))),
            response(status = 200, contentType = "image/webp", body = "image"),
        )

        fixture.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE).close()

        assertEquals(
            listOf(URI(PUBLIC_IMAGE_URL), URI(REDIRECT_IMAGE_URL)),
            fixture.requestedUris,
        )
    }

    @Test
    fun `rejects redirect to non HTTPS address`() {
        val fixture = fixture(
            response(
                status = 302,
                headers = mapOf("Location" to listOf("http://127.0.0.1/internal")),
            ),
        )

        assertFailsWith<PostMediaStorageException> {
            fixture.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE)
        }
    }

    @Test
    fun `rejects media type mismatch`() {
        val fixture = fixture(response(status = 200, contentType = "video/mp4", body = "video"))

        assertFailsWith<PostMediaStorageException> {
            fixture.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE)
        }
    }

    @Test
    fun `rejects declared and streamed bodies larger than configured limit`() {
        val declared = fixture(
            response(
                status = 200,
                contentType = "image/jpeg",
                headers = mapOf("Content-Length" to listOf("6")),
                body = "image!",
            ),
            maxImageBytes = 5,
        )
        val streamed = fixture(
            response(status = 200, contentType = "image/jpeg", body = "image!"),
            maxImageBytes = 5,
        )

        assertFailsWith<PostMediaStorageException> {
            declared.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE)
        }
        assertFailsWith<PostMediaStorageException> {
            streamed.downloader.download(PUBLIC_IMAGE_URL, PostMedia.MediaType.IMAGE)
        }
    }

    private fun fixture(vararg responses: MediaHttpResponse, maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES): Fixture {
        val queue = ArrayDeque(responses.toList())
        val requestedUris = mutableListOf<URI>()
        val client = RemoteMediaHttpClient { uri ->
            requestedUris += uri
            queue.removeFirst()
        }
        val properties = MediaStorageProperties(maxImageBytes = maxImageBytes)
        return Fixture(
            downloader = JdkRemoteMediaDownloader(client, properties, PublicHttpsUriValidator()),
            requestedUris = requestedUris,
        )
    }

    private fun response(
        status: Int,
        contentType: String? = null,
        headers: Map<String, List<String>> = emptyMap(),
        body: String = "",
    ): MediaHttpResponse {
        val responseHeaders = headers.toMutableMap()
        contentType?.let { responseHeaders["Content-Type"] = listOf(it) }
        return MediaHttpResponse(status, responseHeaders, ByteArrayInputStream(body.toByteArray()))
    }

    private data class Fixture(val downloader: JdkRemoteMediaDownloader, val requestedUris: List<URI>)

    private companion object {
        const val PUBLIC_IMAGE_URL = "https://1.1.1.1/image.jpg"
        const val REDIRECT_IMAGE_URL = "https://8.8.8.8/redirected.jpg"
        const val DEFAULT_MAX_IMAGE_BYTES = 1024L
    }
}
