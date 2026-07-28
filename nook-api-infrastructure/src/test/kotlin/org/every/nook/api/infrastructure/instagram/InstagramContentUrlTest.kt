package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.UnsupportedPostUrlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstagramContentUrlTest {
    @Test
    fun `post URL is canonicalized and tracking parameters are removed`() {
        val url = InstagramContentUrl.parse("https://instagram.com/p/Ab_12-xy/?igsh=test#fragment")

        assertEquals("https://www.instagram.com/p/Ab_12-xy/", url.canonicalUrl)
        assertEquals("Ab_12-xy", url.shortcode)
        assertEquals(InstagramContentUrl.Kind.POST, url.kind)
    }

    @Test
    fun `source resolver identifies the shortcode without provider access`() {
        val source = InstagramPostSourceResolver()
            .resolve("https://www.instagram.com/p/Ab_12-xy/?img_index=14&igsh=test")

        assertEquals("INSTAGRAM", source?.type)
        assertEquals("Ab_12-xy", source?.externalPostId)
    }

    @Test
    fun `reel URL is accepted`() {
        val url = InstagramContentUrl.parse("https://www.instagram.com/reel/Reel123/")

        assertEquals(InstagramContentUrl.Kind.REEL, url.kind)
    }

    @Test
    fun `lookalike host is rejected`() {
        assertFailsWith<UnsupportedPostUrlException> {
            InstagramContentUrl.parse("https://instagram.com.evil.example/p/shortcode/")
        }
    }

    @Test
    fun `unsupported path is rejected`() {
        assertFailsWith<UnsupportedPostUrlException> {
            InstagramContentUrl.parse("https://www.instagram.com/stories/example/123/")
        }
    }

    @Test
    fun `non HTTPS URL is rejected`() {
        assertFailsWith<UnsupportedPostUrlException> {
            InstagramContentUrl.parse("http://www.instagram.com/p/shortcode/")
        }
    }
}
