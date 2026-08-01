package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.hamcrest.Matchers.containsString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApifyInstagramPostContentExtractorTest {
    @Test
    fun `post calls synchronous Actor endpoint and stores successful JSON`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v2/acts/test-actor/run-sync-get-dataset-items")))
            .andExpect(requestTo(containsString("format=json")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(
                content().json(
                    """
                    {
                      "directUrls": ["https://www.instagram.com/p/Post123/"],
                      "resultsType": "posts",
                      "resultsLimit": 1
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess(POST_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.extractor.extract("https://www.instagram.com/p/Post123/")

        assertEquals("Post123", result.post.source.externalPostId)
        assertEquals("owner", result.post.authorIdentifier)
        verify(fixture.responseCache).save("APIFY", "INSTAGRAM", "Post123", POST_RESPONSE)
        fixture.server.verify()
    }

    @Test
    fun `cached Apify response skips Actor request`() {
        val responseCache = mock(ScrapingProviderResponseCache::class.java)
        `when`(responseCache.find("APIFY", "INSTAGRAM", "Post123")).thenReturn(POST_RESPONSE)
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val extractor = extractor(builder, responseCache, apiToken = "")

        assertEquals("Post123", extractor.extract("https://www.instagram.com/p/Post123/").post.source.externalPostId)
        server.verify()
    }

    @Test
    fun `no items error is content not found`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(
                withSuccess(
                    """[{"error":"no_items","errorDescription":"Post does not exist"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertFailsWith<PostContentNotFoundException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `unknown Actor item error is provider failure`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess("""[{"error":"internal_error"}]""", MediaType.APPLICATION_JSON))

        assertFailsWith<PostContentProviderException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `gateway timeout is provider timeout`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))

        assertFailsWith<PostContentProviderTimeoutException> {
            fixture.extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    @Test
    fun `missing token fails before Actor request`() {
        val responseCache = mock(ScrapingProviderResponseCache::class.java)
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val extractor = extractor(builder, responseCache, apiToken = "")

        assertFailsWith<PostContentProviderException> {
            extractor.extract("https://www.instagram.com/p/Post123/")
        }
    }

    private fun providerFixture(): ProviderFixture {
        val responseCache = mock(ScrapingProviderResponseCache::class.java)
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return ProviderFixture(
            extractor = extractor(builder, responseCache, apiToken = "test-token"),
            server = server,
            responseCache = responseCache,
        )
    }

    private fun extractor(
        builder: RestClient.Builder,
        responseCache: ScrapingProviderResponseCache,
        apiToken: String,
    ): ApifyInstagramPostContentExtractor = ApifyInstagramPostContentExtractor(
        restClient = builder.build(),
        objectMapper = jacksonObjectMapper(),
        properties = ApifyProperties(apiToken = apiToken, actorId = "test-actor"),
        mapper = ApifyInstagramMapper(),
        responseCache = responseCache,
    )

    private data class ProviderFixture(
        val extractor: ApifyInstagramPostContentExtractor,
        val server: MockRestServiceServer,
        val responseCache: ScrapingProviderResponseCache,
    )

    private companion object {
        val POST_RESPONSE =
            """
            [{
              "type": "Image",
              "shortCode": "Post123",
              "caption": "caption",
              "url": "https://www.instagram.com/p/Post123/",
              "displayUrl": "https://cdn.example/image.jpg",
              "ownerUsername": "owner"
            }]
            """.trimIndent()
    }
}
