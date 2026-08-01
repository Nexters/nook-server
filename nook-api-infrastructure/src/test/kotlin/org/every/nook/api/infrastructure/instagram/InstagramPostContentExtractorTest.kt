package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstagramPostContentExtractorTest {
    @Test
    fun `missing configuration uses Bright Data with Apify fallback`() {
        val brightData = extractor(result = CONTENT)
        val apify = extractor(result = CONTENT)
        val router = router(null, brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(1, brightData.calls)
        assertEquals(0, apify.calls)
    }

    @Test
    fun `Bright Data only never calls Apify`() {
        val brightData = extractor(failure = PostContentProviderException())
        val apify = extractor(result = CONTENT)
        val router = router("BRIGHT_DATA_ONLY", brightData, apify)

        assertFailsWith<PostContentProviderException> { router.extract(URL) }
        assertEquals(1, brightData.calls)
        assertEquals(0, apify.calls)
    }

    @Test
    fun `Apify only skips Bright Data`() {
        val brightData = extractor(result = CONTENT)
        val apify = extractor(result = CONTENT)
        val router = router("APIFY_ONLY", brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(0, brightData.calls)
        assertEquals(1, apify.calls)
    }

    @Test
    fun `provider failure falls back to Apify once`() {
        val brightData = extractor(failure = PostContentProviderException())
        val apify = extractor(result = CONTENT)
        val router = router("BRIGHT_DATA_WITH_APIFY_FALLBACK", brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(1, brightData.calls)
        assertEquals(1, apify.calls)
    }

    @Test
    fun `provider timeout falls back to Apify once`() {
        val brightData = extractor(failure = PostContentProviderTimeoutException())
        val apify = extractor(result = CONTENT)
        val router = router("BRIGHT_DATA_WITH_APIFY_FALLBACK", brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(1, brightData.calls)
        assertEquals(1, apify.calls)
    }

    @Test
    fun `content not found does not fall back`() {
        val brightData = extractor(failure = PostContentNotFoundException())
        val apify = extractor(result = CONTENT)
        val router = router("BRIGHT_DATA_WITH_APIFY_FALLBACK", brightData, apify)

        assertFailsWith<PostContentNotFoundException> { router.extract(URL) }
        assertEquals(1, brightData.calls)
        assertEquals(0, apify.calls)
    }

    @Test
    fun `Apify provider failure falls back to Bright Data once`() {
        val brightData = extractor(result = CONTENT)
        val apify = extractor(failure = PostContentProviderException())
        val router = router("APIFY_BRIGHT_WITH_DATA_FALLBACK", brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(1, brightData.calls)
        assertEquals(1, apify.calls)
    }

    @Test
    fun `Apify timeout falls back to Bright Data once`() {
        val brightData = extractor(result = CONTENT)
        val apify = extractor(failure = PostContentProviderTimeoutException())
        val router = router("APIFY_BRIGHT_WITH_DATA_FALLBACK", brightData, apify)

        assertEquals(CONTENT, router.extract(URL))
        assertEquals(1, brightData.calls)
        assertEquals(1, apify.calls)
    }

    @Test
    fun `Apify content not found does not fall back`() {
        val brightData = extractor(result = CONTENT)
        val apify = extractor(failure = PostContentNotFoundException())
        val router = router("APIFY_BRIGHT_WITH_DATA_FALLBACK", brightData, apify)

        assertFailsWith<PostContentNotFoundException> { router.extract(URL) }
        assertEquals(0, brightData.calls)
        assertEquals(1, apify.calls)
    }

    private fun router(
        mode: String?,
        brightData: RecordingExtractor,
        apify: RecordingExtractor,
    ): InstagramPostContentExtractor = InstagramPostContentExtractor(
        brightDataExtractor = brightData,
        apifyExtractor = apify,
        configurationReader = RuntimeConfigurationReader { mode },
    )

    private fun extractor(
        result: ExtractedPostContent? = null,
        failure: RuntimeException? = null,
    ): RecordingExtractor = RecordingExtractor(result, failure)

    private class RecordingExtractor(
        private val result: ExtractedPostContent?,
        private val failure: RuntimeException?,
    ) : PostContentExtractor {
        var calls: Int = 0
            private set

        override fun supports(url: String): Boolean = true

        override fun extract(url: String): ExtractedPostContent {
            calls += 1
            failure?.let { throw it }
            return requireNotNull(result)
        }
    }

    private companion object {
        const val URL = "https://www.instagram.com/p/Post123/"
        val CONTENT = ExtractedPostContent(
            post = Post(
                source = PostSource("INSTAGRAM", "Post123"),
                canonicalUrl = URL,
                authorIdentifier = null,
                body = null,
                publishedAt = null,
                media = emptyList(),
            ),
            hashtags = emptyList(),
            sourceLocationNames = emptyList(),
        )
    }
}
