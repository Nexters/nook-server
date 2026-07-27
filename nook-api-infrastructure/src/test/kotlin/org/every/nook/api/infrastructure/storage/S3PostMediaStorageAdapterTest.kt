package org.every.nook.api.infrastructure.storage

import org.every.nook.api.domain.post.PostMedia
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S3PostMediaStorageAdapterTest {
    @Test
    fun `uploads content addressed object and returns CloudFront URL`() {
        val objectStorage = RecordingObjectStorage()
        val adapter = adapter(objectStorage)

        val result = adapter.store(SOURCE_MEDIA)

        val expectedKey =
            "post-media/sha256/ba/ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.jpg"
        assertEquals("https://media.example/$expectedKey", result.url)
        assertEquals(listOf(expectedKey), objectStorage.existenceChecks)
        assertEquals(expectedKey, objectStorage.uploads.single().key)
        assertEquals("image/jpeg", objectStorage.uploads.single().contentType)
        assertContentEquals("abc".toByteArray(), objectStorage.uploads.single().content)
    }

    @Test
    fun `does not upload object that already exists`() {
        val objectStorage = RecordingObjectStorage(objectExists = true)
        val adapter = adapter(objectStorage)

        adapter.store(SOURCE_MEDIA)

        assertEquals(1, objectStorage.existenceChecks.size)
        assertTrue(objectStorage.uploads.isEmpty())
    }

    private fun adapter(objectStorage: RecordingObjectStorage): S3PostMediaStorageAdapter {
        val writer = DownloadedMediaFileWriter()
        val downloader = RemoteMediaDownloader { _, _ ->
            writer.persist(
                input = ByteArrayInputStream("abc".toByteArray()),
                contentType = "image/jpeg",
                extension = "jpg",
                maxBytes = 3,
            )
        }
        val properties = MediaStorageProperties(
            enabled = true,
            bucket = "nook-media",
            cloudFrontBaseUrl = "https://media.example/",
        )
        return S3PostMediaStorageAdapter(downloader, objectStorage, properties)
    }

    private class RecordingObjectStorage(private val objectExists: Boolean = false) : MediaObjectStorage {
        val existenceChecks = mutableListOf<String>()
        val uploads = mutableListOf<Upload>()

        override fun exists(key: String): Boolean {
            existenceChecks += key
            return objectExists
        }

        override fun put(key: String, path: Path, contentType: String, contentLength: Long) {
            uploads += Upload(key, Files.readAllBytes(path), contentType, contentLength)
        }
    }

    private data class Upload(
        val key: String,
        val content: ByteArray,
        val contentType: String,
        val contentLength: Long,
    )

    private companion object {
        val SOURCE_MEDIA = PostMedia(PostMedia.MediaType.IMAGE, "https://1.1.1.1/image.jpg", 0)
    }
}
