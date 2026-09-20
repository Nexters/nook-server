package org.every.nook.api.infrastructure.storage

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MediaStoragePropertiesTest {
    @Test
    fun `download timeout must be positive`() {
        for (timeout in listOf(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertFailsWith<IllegalArgumentException> {
                MediaStorageProperties(readTimeout = timeout)
            }
        }
    }

    @Test
    fun `enabled storage requires bucket and HTTPS CloudFront URL`() {
        assertFailsWith<IllegalArgumentException> {
            MediaStorageProperties(enabled = true)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaStorageProperties(
                enabled = true,
                bucket = "nook-media",
                cloudFrontBaseUrl = "http://media.example",
            )
        }
    }
}
