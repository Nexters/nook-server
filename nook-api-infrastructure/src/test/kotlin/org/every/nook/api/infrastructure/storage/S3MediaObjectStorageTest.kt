package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S3MediaObjectStorageTest {
    @Test
    fun `checks object existence with configured bucket`() {
        var capturedRequest: HeadObjectRequest? = null
        val storage = storage { method, arguments ->
            if (method == "headObject") {
                capturedRequest = arguments.single() as HeadObjectRequest
                HeadObjectResponse.builder().build()
            } else {
                null
            }
        }

        assertTrue(storage.exists("post-media/file.jpg"))
        assertEquals("nook-media", capturedRequest?.bucket())
        assertEquals("post-media/file.jpg", capturedRequest?.key())
    }

    @Test
    fun `missing object returns false and other S3 failures are translated`() {
        val missing = storage { method, _ ->
            if (method == "headObject") {
                throw S3Exception.builder().statusCode(404).message("missing").build()
            }
            null
        }
        val failed = storage { method, _ ->
            if (method == "headObject") {
                throw S3Exception.builder().statusCode(500).message("failed").build()
            }
            null
        }

        assertFalse(missing.exists("post-media/missing.jpg"))
        assertFailsWith<PostMediaStorageException> {
            failed.exists("post-media/failed.jpg")
        }
    }

    @Test
    fun `uploads immutable object metadata`() {
        var capturedRequest: PutObjectRequest? = null
        val storage = storage { method, arguments ->
            if (method == "putObject") {
                capturedRequest = arguments.first() as PutObjectRequest
                PutObjectResponse.builder().build()
            } else {
                null
            }
        }
        val path = Files.createTempFile("s3-storage-test-", ".jpg")
        Files.writeString(path, "abc")

        try {
            storage.put("post-media/file.jpg", path, "image/jpeg", 3)
        } finally {
            Files.deleteIfExists(path)
        }

        assertEquals("nook-media", capturedRequest?.bucket())
        assertEquals("post-media/file.jpg", capturedRequest?.key())
        assertEquals("image/jpeg", capturedRequest?.contentType())
        assertEquals(3, capturedRequest?.contentLength())
        assertEquals("public, max-age=31536000, immutable", capturedRequest?.cacheControl())
    }

    private fun storage(invocation: (String, Array<out Any?>) -> Any?): S3MediaObjectStorage {
        val client = Proxy.newProxyInstance(
            S3Client::class.java.classLoader,
            arrayOf(S3Client::class.java),
        ) { _, method, arguments ->
            invocation(method.name, arguments ?: emptyArray())
        } as S3Client
        val properties = MediaStorageProperties(
            enabled = true,
            bucket = "nook-media",
            cloudFrontBaseUrl = "https://media.example",
        )
        return S3MediaObjectStorage(client, properties)
    }
}
