package org.every.nook.api.infrastructure.openai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.post.PostTitleGenerator
import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiContentInferenceAdapterTest {
    @Test
    fun `generates a title with structured output`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(containsString("post_title")))
            .andExpect(content().string(containsString("홍보성")))
            .andExpect(content().string(containsString("\"maxLength\":25")))
            .andRespond(withSuccess(response("""{"title":"용산 미나리 삼겹살"}"""), MediaType.APPLICATION_JSON))

        val title = fixture.adapter.generate(
            PostTitleGenerator.Request("원동미나리삼겹살 방문", listOf("용산맛집"), "용산구"),
        )

        assertEquals("용산 미나리 삼겹살", title)
        fixture.server.verify()
    }

    @Test
    fun `extracts multiple grounded place clues`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("place_clues")))
            .andExpect(content().string(containsString("\"maxItems\":10")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "places": [
                            {"name":"원동미나리삼겹살","region":"용산구","queries":["용산 원동미나리삼겹살"]},
                            {"name":"서울역","region":null,"queries":["서울역"]}
                          ]
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val places = fixture.adapter.extract(PlaceClueExtractor.Request("두 장소 방문", emptyList(), null))

        assertEquals(listOf("원동미나리삼겹살", "서울역"), places.map { it.name })
        assertEquals(null, places.last().region)
    }

    @Test
    fun `fails when OpenAI refuses the request`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andRespond(withSuccess(REFUSAL_RESPONSE, MediaType.APPLICATION_JSON))

        assertFailsWith<IllegalStateException> {
            fixture.adapter.generate(PostTitleGenerator.Request(null, emptyList(), null))
        }
    }

    private fun adapterFixture(): AdapterFixture {
        val builder = RestClient.builder().baseUrl("https://api.openai.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return AdapterFixture(
            adapter = OpenAiContentInferenceAdapter(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = OpenAiProperties(apiKey = "test-key"),
            ),
            server = server,
        )
    }

    private data class AdapterFixture(val adapter: OpenAiContentInferenceAdapter, val server: MockRestServiceServer)

    private companion object {
        fun response(output: String): String =
            """
            {
              "output": [{
                "type": "message",
                "content": [{"type":"output_text","text":${jacksonObjectMapper().writeValueAsString(output)}}]
              }]
            }
            """.trimIndent()

        val REFUSAL_RESPONSE =
            """
            {
              "output": [{
                "type": "message",
                "content": [{"type":"refusal","refusal":"cannot comply"}]
              }]
            }
            """.trimIndent()
    }
}
