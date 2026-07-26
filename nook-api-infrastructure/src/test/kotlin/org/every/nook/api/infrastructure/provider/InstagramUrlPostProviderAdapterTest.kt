package org.every.nook.api.infrastructure.provider

import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstagramUrlPostProviderAdapterTest {
    private val adapter = InstagramUrlPostProviderAdapter()

    @Test
    fun `normalizes a supported Instagram reel URL`() {
        val post = adapter.fetch("https://instagram.com/reel/ABC_123/?utm_source=share")

        assertEquals("INSTAGRAM", post.source.type)
        assertEquals("ABC_123", post.source.externalPostId)
        assertEquals("https://www.instagram.com/reel/ABC_123/", post.canonicalUrl)
    }

    @Test
    fun `rejects an unsupported Instagram URL`() {
        assertFailsWith<InvalidInstagramPostUrlException> {
            adapter.fetch("https://www.instagram.com/stories/nook/123/")
        }
    }
}
