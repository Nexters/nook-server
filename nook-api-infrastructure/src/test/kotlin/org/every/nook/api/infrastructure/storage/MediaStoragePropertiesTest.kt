package org.every.nook.api.infrastructure.storage

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MediaStoragePropertiesTest {
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
