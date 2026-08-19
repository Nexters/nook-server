package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApifyNaverPlaceThumbnailProviderTest {
    @Test
    fun `requests place details and stores up to six photos from matching Naver place`() {
        val fixture = fixture()
        expectSearch(fixture.server)
        expectDetails(fixture.server)
        val result = fixture.provider.fetch(REQUEST)

        assertEquals(6, result?.photoUrls?.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), fixture.storage.stored.map(PostMedia::sequence))
        fixture.server.verify()
    }

    private fun expectSearch(server: MockRestServiceServer) {
        server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().json(SEARCH_INPUT))
            .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON))
    }

    private fun expectDetails(server: MockRestServiceServer) {
        server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json(DETAIL_INPUT))
            .andRespond(withSuccess(DETAIL_RESPONSE, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `rejects a different place even when Actor returns photos`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(
                withSuccess(
                    """
                    [{
                      "Name": "다른 식당",
                      "Address": "부산광역시 해운대구 해운대로 1",
                      "Latitude": "35.1587",
                      "Longitude": "129.1604",
                      "Images": ["https://naver.example/wrong.jpg"]
                    }]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertNull(fixture.provider.fetch(REQUEST))
        assertEquals(emptyList(), fixture.storage.stored)
    }

    @Test
    fun `missing token skips Actor`() {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = ApifyNaverPlaceThumbnailProvider(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = ApifyNaverPlaceProperties(apiToken = ""),
            mediaStorage = FakeStorage(),
        )

        assertNull(provider.fetch(REQUEST))
        server.verify()
    }

    private fun fixture(): Fixture {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val storage = FakeStorage()
        return Fixture(
            provider = ApifyNaverPlaceThumbnailProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = ApifyNaverPlaceProperties(apiToken = "test-token", actorId = "test-actor"),
                mediaStorage = storage,
            ),
            server = server,
            storage = storage,
        )
    }

    private class FakeStorage : PostMediaStoragePort {
        val stored = mutableListOf<PostMedia>()

        override fun store(media: PostMedia): PostMedia {
            stored += media
            return media.copy(url = "https://cdn.example/${media.sequence}.jpg")
        }
    }

    private data class Fixture(
        val provider: ApifyNaverPlaceThumbnailProvider,
        val server: MockRestServiceServer,
        val storage: FakeStorage,
    )

    private companion object {
        const val SEARCH_INPUT =
            """{"keywords":["누크 카페 서울 강남구 테헤란로 1"],"scrapePlaceDetails":false,"maxResultsPerKeyword":5}"""
        val SEARCH_RESPONSE = """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 1",
            "Latitude":"37.5001","Longitude":"127.0001","PlaceId":"123",
            "NaverMapUrl":"https://map.naver.com/p/entry/place/123",
            "SearchKeyword":"누크 카페 서울 강남구 테헤란로 1"}]
        """.trimIndent()
        const val DETAIL_INPUT =
            """{"urls":["https://map.naver.com/p/entry/place/123"],"scrapePlaceDetails":true}"""
        val DETAIL_RESPONSE = """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 1","PlaceId":"123",
            "NaverMapUrl":"https://map.naver.com/p/entry/place/123","Images":[
            "https://naver.example/1.jpg",{"url":"https://naver.example/2.jpg"},
            "https://naver.example/3.jpg","https://naver.example/4.jpg","https://naver.example/5.jpg",
            "https://naver.example/6.jpg","https://naver.example/7.jpg"]}]
        """.trimIndent()
        val REQUEST = PlaceThumbnailProvider.Request(
            place = PlaceCandidate(
                provider = "NAVER",
                externalPlaceId = "place-id",
                name = "누크 카페",
                address = "서울 강남구 테헤란로 1",
                latitude = BigDecimal("37.5000"),
                longitude = BigDecimal("127.0000"),
                category = null,
                phoneNumber = null,
                providerUrl = null,
            ),
        )
    }
}
