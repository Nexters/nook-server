package org.every.nook.api.application.content

import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExtractPostContentUseCaseTest {
    @Test
    fun `selects the extractor that supports the URL`() {
        val unsupported = extractor(supports = false, sourceType = "UNSUPPORTED")
        val supported = extractor(supports = true, sourceType = "INSTAGRAM")
        val useCase = ExtractPostContentUseCase(listOf(unsupported, supported))

        val result = useCase("https://www.instagram.com/p/ABC123/")

        assertEquals("INSTAGRAM", result.post.source.type)
    }

    @Test
    fun `rejects a URL when no extractor supports it`() {
        val useCase = ExtractPostContentUseCase(emptyList())

        assertFailsWith<UnsupportedPostUrlException> {
            useCase("https://example.com/post/1")
        }
    }

    private fun extractor(supports: Boolean, sourceType: String): PostContentExtractor = object : PostContentExtractor {
        override fun supports(url: String): Boolean = supports

        override fun extract(url: String): ExtractedPostContent = ExtractedPostContent(
            post = Post(
                source = PostSource(sourceType, "ABC123"),
                canonicalUrl = url,
            ),
            hashtags = emptyList(),
            sourceLocationNames = emptyList(),
        )
    }
}
