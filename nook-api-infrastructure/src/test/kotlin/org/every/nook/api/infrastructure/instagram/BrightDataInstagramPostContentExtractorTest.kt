package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.hamcrest.Matchers.containsString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BrightDataInstagramPostContentExtractorTest {
    @Test
    fun `cached response skips Bright Data request`() {
        val responseCache = mock(ScrapingProviderResponseCache::class.java)
        `when`(responseCache.find("BRIGHT_DATA", "INSTAGRAM", "Post123")).thenReturn(
            """[{"url":"https://www.instagram.com/p/Post123/","content_type":"Image"}]""",
        )
        val builder = RestClient.builder().baseUrl("https://api.brightdata.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val extractor = BrightDataInstagramPostContentExtractor(
            builder.build(),
            jacksonObjectMapper(),
            BrightDataProperties(apiToken = ""),
            BrightDataInstagramMapper(),
            responseCache,
        )

        assertEquals("Post123", extractor.extract("https://www.instagram.com/p/Post123/").post.source.externalPostId)
        server.verify()
    }

    @Test
    fun `post uses posts dataset and bearer token`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("dataset_id=posts-dataset")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(
                withSuccess(
                    """
                    [{
                      "url": "https://www.instagram.com/p/Post123/",
                      "content_type": "Image",
                      "photos": ["https://cdn.example/photo.jpg"]
                    }]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.extractor.extract("https://www.instagram.com/p/Post123/")

        assertEquals("Post123", result.post.source.externalPostId)
        fixture.server.verify()
    }

    @Test
    fun `reel uses reels dataset`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("dataset_id=reels-dataset")))
            .andRespond(
                withSuccess(
                    """
                    [{
                      "url": "https://www.instagram.com/reel/Reel123/",
                      "video_url": "https://cdn.example/reel.mp4"
                    }]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        fixture.extractor.extract("https://www.instagram.com/reel/Reel123/")

        fixture.server.verify()
    }

    @Test
    fun `empty result is not found`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/datasets/v3/scrape")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        assertFailsWith<PostContentNotFoundException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `snapshot response is treated as provider timeout`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/datasets/v3/scrape")))
            .andRespond(withSuccess("""{"snapshot_id":"s_123"}""", MediaType.APPLICATION_JSON))

        assertFailsWith<PostContentProviderTimeoutException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `provider 404 is content not found`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/datasets/v3/scrape")))
            .andRespond(withResourceNotFound())

        assertFailsWith<PostContentNotFoundException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `missing API token fails without external request`() {
        val builder = RestClient.builder().baseUrl("https://api.brightdata.test")
        val extractor = BrightDataInstagramPostContentExtractor(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = BrightDataProperties(apiToken = ""),
            mapper = BrightDataInstagramMapper(),
            responseCache = mock(ScrapingProviderResponseCache::class.java),
        )

        assertFailsWith<PostContentProviderException> {
            extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    private fun providerFixture(): ProviderFixture {
        val builder = RestClient.builder().baseUrl("https://api.brightdata.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return ProviderFixture(
            extractor = BrightDataInstagramPostContentExtractor(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = BrightDataProperties(
                    apiToken = "test-token",
                    postsDatasetId = "posts-dataset",
                    reelsDatasetId = "reels-dataset",
                ),
                mapper = BrightDataInstagramMapper(),
                responseCache = mock(ScrapingProviderResponseCache::class.java),
            ),
            server = server,
        )
    }

    private data class ProviderFixture(
        val extractor: BrightDataInstagramPostContentExtractor,
        val server: MockRestServiceServer,
    )
}
