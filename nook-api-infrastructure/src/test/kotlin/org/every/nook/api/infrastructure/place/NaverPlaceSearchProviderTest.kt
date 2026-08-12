package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaverPlaceSearchProviderTest {
    @Test
    fun `uses Naver API Hub local search credentials`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/search/v1/local")))
            .andExpect(requestTo(containsString("query=Nook%20Cafe")))
            .andExpect(requestTo(containsString("display=5")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-client-id"))
            .andExpect(header("X-NCP-APIGW-API-KEY", "test-client-secret"))
            .andExpect(header("Accept", containsString(MediaType.APPLICATION_JSON_VALUE)))
            .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))

        assertEquals("NAVER", result.single().provider)
        fixture.server.verify()
    }

    @Test
    fun `provider error is converted to application exception`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/search/v1/local")))
            .andRespond(withServerError())

        assertFailsWith<PlaceSearchProviderException> {
            fixture.provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))
        }
    }

    @Test
    fun `missing credentials fail without request`() {
        val provider = NaverPlaceSearchProvider(
            restClient = RestClient.builder().baseUrl("https://naver.test").build(),
            objectMapper = jacksonObjectMapper(),
            properties = NaverPlaceProperties(clientId = "", clientSecret = ""),
            mapper = NaverPlaceMapper(),
        )

        assertFailsWith<PlaceSearchProviderException> {
            provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))
        }
    }

    private fun providerFixture(): ProviderFixture {
        val builder = RestClient.builder().baseUrl("https://naver.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return ProviderFixture(
            provider = NaverPlaceSearchProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = NaverPlaceProperties(clientId = "test-client-id", clientSecret = "test-client-secret"),
                mapper = NaverPlaceMapper(),
            ),
            server = server,
        )
    }

    private data class ProviderFixture(val provider: NaverPlaceSearchProvider, val server: MockRestServiceServer)

    private companion object {
        val SUCCESS_RESPONSE =
            """
            {
              "items": [{
                "title": "<b>Nook Cafe</b>",
                "link": "https://map.naver.com/place/1",
                "category": "카페",
                "address": "서울특별시 용산구 갈월동 99-1",
                "roadAddress": "서울특별시 용산구 한강대로77길 4-1",
                "mapx": "126.972332",
                "mapy": "37.543123"
              }]
            }
            """.trimIndent()
    }
}
