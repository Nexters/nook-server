package org.every.nook.api.infrastructure.storage

import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.cache.MediaUrlCacheEntity
import org.every.nook.api.infrastructure.persistence.cache.MediaUrlCacheJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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

    @Test
    fun `returns cached URL without downloading or accessing object storage`() {
        val objectStorage = RecordingObjectStorage()
        val cacheRepository = mock(MediaUrlCacheJpaRepository::class.java)
        `when`(cacheRepository.findBySourceUrlHash(SOURCE_URL_HASH)).thenReturn(
            MediaUrlCacheEntity(SOURCE_URL_HASH, SOURCE_MEDIA.url, "https://media.example/cached.jpg"),
        )

        val result = adapter(objectStorage, cacheRepository).store(SOURCE_MEDIA)

        assertEquals("https://media.example/cached.jpg", result.url)
        assertTrue(objectStorage.existenceChecks.isEmpty())
        assertTrue(objectStorage.uploads.isEmpty())
    }

    private fun adapter(
        objectStorage: RecordingObjectStorage,
        cacheRepository: MediaUrlCacheJpaRepository = mock(MediaUrlCacheJpaRepository::class.java),
    ): S3PostMediaStorageAdapter {
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
        `when`(cacheRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MediaUrlCacheEntity::class.java)))
            .thenAnswer { it.arguments[0] }
        return S3PostMediaStorageAdapter(downloader, objectStorage, properties, cacheRepository)
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
        const val SOURCE_URL_HASH = "525f5dbf32b7f6a901b6e41e4c2d394928ad20a184d50f4a3e8f56b901d7950e"
    }
}
