package org.every.nook.api.domain.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostTest {
    @Test
    fun `accepts an extensible source type`() {
        val source = PostSource(type = "NAVER_BLOG", externalPostId = "writer:123")

        val post = Post(source = source, canonicalUrl = "https://example.com/post/123")

        assertEquals("NAVER_BLOG", post.source.type)
    }

    @Test
    fun `rejects duplicate media sequence`() {
        val source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123")
        val first = PostMedia(PostMedia.MediaType.IMAGE, "https://example.com/1.jpg", sequence = 0)
        val second = PostMedia(PostMedia.MediaType.IMAGE, "https://example.com/2.jpg", sequence = 0)

        assertFailsWith<IllegalArgumentException> {
            Post(
                source = source,
                canonicalUrl = "https://www.instagram.com/p/ABC123/",
                media = listOf(first, second),
            )
        }
    }

    @Test
    fun `rejects blank external post id`() {
        assertFailsWith<IllegalArgumentException> {
            PostSource(type = "INSTAGRAM", externalPostId = " ")
        }
    }

    @Test
    fun `rejects non canonical source type`() {
        assertFailsWith<IllegalArgumentException> {
            PostSource(type = "instagram", externalPostId = "ABC123")
        }
    }
}
