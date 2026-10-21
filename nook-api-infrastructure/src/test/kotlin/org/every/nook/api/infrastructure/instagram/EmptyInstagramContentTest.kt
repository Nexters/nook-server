package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmptyInstagramContentTest {
    @Test
    fun `empty provider records fail without being cached`() {
        listOf("APIFY", "BRIGHT_DATA").forEach { provider ->
            listOf("[{}]", """[{"description":"  ","caption":"  ","post_content":[{"url":null}]}]""")
                .forEach { response ->
                    val fixture = fixture(provider)
                    fixture.respond(response)
                    assertFailsWith<PostContentProviderException> { fixture.extractor.extract(URL) }
                    verify(fixture.cache, never()).save(anyString(), anyString(), anyString(), anyString())
                    fixture.server.verify()
                }
        }
    }

    @Test
    fun `body only and media only responses remain usable`() {
        listOf("APIFY", "BRIGHT_DATA").forEach { provider ->
            val responses = if (provider == "APIFY") {
                listOf("""[{"caption":"caption"}]""", """[{"displayUrl":"https://cdn.test/image.jpg"}]""")
            } else {
                listOf("""[{"description":"caption"}]""", """[{"photos":["https://cdn.test/image.jpg"]}]""")
            }
            responses.forEach { response ->
                val fixture = fixture(provider)
                fixture.respond(response)
                fixture.extractor.extract(URL)
                verify(fixture.cache).save(provider, "INSTAGRAM", "Post123", response)
                fixture.server.verify()
            }
        }
    }

    @Test
    fun `invalid cached responses are evicted and replaced after fresh successful collection`() {
        listOf("APIFY", "BRIGHT_DATA").forEach { provider ->
            listOf("[{}]", "[]", "not-json").forEach { cached ->
                val fixture = fixture(provider)
                `when`(fixture.cache.find(provider, "INSTAGRAM", "Post123")).thenReturn(cached)
                val fresh = """[{"caption":"recovered","description":"recovered"}]"""
                fixture.respond(fresh)
                assertEquals("recovered", fixture.extractor.extract(URL).post.body)
                verify(fixture.cache).evict(provider, "INSTAGRAM", "Post123", cached)
                verify(fixture.cache).save(provider, "INSTAGRAM", "Post123", fresh)
                fixture.server.verify()
            }
        }
    }

    @Test
    fun `empty cached content followed by empty fresh content still fails without saving`() {
        listOf("APIFY", "BRIGHT_DATA").forEach { provider ->
            val fixture = fixture(provider)
            `when`(fixture.cache.find(provider, "INSTAGRAM", "Post123")).thenReturn("[{}]")
            fixture.respond("[{}]")
            assertFailsWith<PostContentProviderException> { fixture.extractor.extract(URL) }
            verify(fixture.cache).evict(provider, "INSTAGRAM", "Post123", "[{}]")
            verify(fixture.cache, never()).save(anyString(), anyString(), anyString(), anyString())
            fixture.server.verify()
        }
    }

    @Test
    fun `empty Apify content falls back and empty Bright Data content propagates failure`() {
        val apify = fixture("APIFY")
        val bright = fixture("BRIGHT_DATA")
        apify.respond("[{}]")
        bright.respond("[{}]")
        val router = InstagramPostContentExtractor(
            bright.extractor,
            apify.extractor,
            RuntimeConfigurationReader { "APIFY_BRIGHT_WITH_DATA_FALLBACK" },
        )
        assertFailsWith<PostContentProviderException> { router.extract(URL) }
        apify.server.verify()
        bright.server.verify()
    }

    @Test
    fun `production metadata only response fails and never enters cache`() {
        val fixture = fixture("BRIGHT_DATA")
        fixture.respond(
            """[{"shortcode":"DeUL9gVEtup","content_type":"Image","post_id":"4004878573752212393",
              "post_content":[{"type":"Video","url":null,"thumbnail":null}],"images":[],"photos_number":0}]
            """.trimIndent(),
        )
        assertFailsWith<PostContentProviderException> { fixture.extractor.extract(URL) }
        verify(fixture.cache, never()).save(anyString(), anyString(), anyString(), anyString())
        fixture.server.verify()
    }

    @Test
    fun `empty Apify response recovers through usable Bright Data content`() {
        val apify = fixture("APIFY")
        val bright = fixture("BRIGHT_DATA")
        apify.respond("[{}]")
        bright.respond("""[{"description":"recovered"}]""")
        val router = InstagramPostContentExtractor(
            bright.extractor,
            apify.extractor,
            RuntimeConfigurationReader { "APIFY_BRIGHT_WITH_DATA_FALLBACK" },
        )
        assertEquals("recovered", router.extract(URL).post.body)
        apify.server.verify()
        bright.server.verify()
    }

    private fun fixture(provider: String): Fixture {
        val cache = mock(ScrapingProviderResponseCache::class.java)
        val builder = RestClient.builder().baseUrl("https://provider.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val extractor = if (provider == "APIFY") {
            ApifyInstagramPostContentExtractor(
                builder.build(),
                jacksonObjectMapper(),
                ApifyProperties(apiToken = "test"),
                ApifyInstagramMapper(),
                cache,
            )
        } else {
            BrightDataInstagramPostContentExtractor(
                builder.build(),
                jacksonObjectMapper(),
                BrightDataProperties(apiToken = "test"),
                BrightDataInstagramMapper(),
                cache,
            )
        }
        return Fixture(extractor, server, cache)
    }

    private data class Fixture(
        val extractor: PostContentExtractor,
        val server: MockRestServiceServer,
        val cache: ScrapingProviderResponseCache,
    ) {
        fun respond(response: String) {
            server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
        }
    }

    private companion object {
        const val URL = "https://www.instagram.com/p/Post123/"
    }
}
