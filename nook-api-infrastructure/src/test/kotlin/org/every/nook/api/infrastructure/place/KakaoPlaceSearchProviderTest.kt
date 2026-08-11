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
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoPlaceSearchProviderTest {
    @Test
    fun `uses REST API key and optional location filters`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
            .andExpect(requestTo(containsString("query=Nook%20Cafe")))
            .andExpect(requestTo(containsString("x=127.1")))
            .andExpect(requestTo(containsString("y=37.1")))
            .andExpect(requestTo(containsString("radius=1000")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "KakaoAK test-rest-api-key"))
            .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.search(
            PlaceSearchProvider.Request(
                query = "Nook Cafe",
                longitude = BigDecimal("127.1"),
                latitude = BigDecimal("37.1"),
                radius = 1000,
            ),
        )

        assertEquals("26338954", result.single().externalPlaceId)
        fixture.server.verify()
    }

    @Test
    fun `empty Kakao documents return empty candidates`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
            .andRespond(withSuccess("""{"documents":[]}""", MediaType.APPLICATION_JSON))

        val result = fixture.provider.search(PlaceSearchProvider.Request(query = "없는 장소"))

        assertEquals(emptyList(), result)
    }

    @Test
    fun `returns provider pagination metadata and distance`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("page=2")))
            .andExpect(requestTo(containsString("size=10")))
            .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.searchPage(
            PlaceSearchProvider.Request(
                query = "Nook Cafe",
                page = 2,
                size = 10,
            ),
        )

        assertEquals(2, result.page)
        assertEquals(10, result.size)
        assertEquals(true, result.hasNext)
        assertEquals(418, result.items.single().distanceMeters)
    }

    @Test
    fun `provider error is converted to application exception`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
            .andRespond(withServerError())

        assertFailsWith<PlaceSearchProviderException> {
            fixture.provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))
        }
    }

    @Test
    fun `missing REST API key fails without request`() {
        val provider = KakaoPlaceSearchProvider(
            restClient = RestClient.builder().baseUrl("https://dapi.kakao.test").build(),
            objectMapper = jacksonObjectMapper(),
            properties = KakaoPlaceProperties(restApiKey = ""),
            mapper = KakaoPlaceMapper(),
        )

        assertFailsWith<PlaceSearchProviderException> {
            provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))
        }
    }

    private fun providerFixture(): ProviderFixture {
        val builder = RestClient.builder().baseUrl("https://dapi.kakao.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return ProviderFixture(
            provider = KakaoPlaceSearchProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = KakaoPlaceProperties(restApiKey = "test-rest-api-key"),
                mapper = KakaoPlaceMapper(),
            ),
            server = server,
        )
    }

    private data class ProviderFixture(val provider: KakaoPlaceSearchProvider, val server: MockRestServiceServer)

    private companion object {
        val SUCCESS_RESPONSE =
            """
            {
              "meta": {
                "pageable_count": 14,
                "is_end": false
              },
              "documents": [{
                "id": "26338954",
                "place_name": "Nook Cafe",
                "category_name": "음식점 > 카페",
                "phone": "02-1234-5678",
                "address_name": "서울 성동구 성수동 1",
                "road_address_name": "서울 성동구 아차산로 1",
                "x": "127.0590297",
                "y": "37.5120741",
                "place_url": "https://place.map.kakao.com/26338954",
                "distance": "418"
              }]
            }
            """.trimIndent()
    }
}
