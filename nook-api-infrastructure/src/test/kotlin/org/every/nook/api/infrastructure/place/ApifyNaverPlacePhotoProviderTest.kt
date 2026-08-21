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

class ApifyNaverPlacePhotoProviderTest {
    @Test
    fun `stores up to three business photos for a verified Naver place`() {
        val fixture = fixture()
        expectSearch(fixture.server, MATCHING_SEARCH_RESPONSE)
        expectPhotos(fixture.server, PHOTO_RESPONSE)

        val result = fixture.provider.fetch(REQUEST)

        assertEquals(
            listOf("https://cdn.example/0.jpg", "https://cdn.example/1.jpg", "https://cdn.example/2.jpg"),
            result?.photoUrls,
        )
        assertEquals(
            listOf("https://naver.example/1.jpg", "https://naver.example/2.jpg", "https://naver.example/3.jpg"),
            fixture.storage.stored.map(PostMedia::url).sorted(),
        )
        fixture.server.verify()
    }

    @Test
    fun `rejects a same-name place when both address and coordinates differ`() {
        val fixture = fixture()
        expectSearch(
            fixture.server,
            """
            [{"Name":"누크 카페","FullAddress":"부산광역시 해운대구 해운대로 1",
              "Latitude":"35.1587","Longitude":"129.1604","PlaceId":"999",
              "NaverMapUrl":"https://map.naver.com/p/entry/place/999",
              "SearchKeyword":"누크 카페 서울 강남구 테헤란로 1"}]
            """.trimIndent(),
        )

        assertNull(fixture.provider.fetch(REQUEST))
        assertEquals(emptyList(), fixture.storage.stored)
        fixture.server.verify()
    }

    @Test
    fun `ignores visitor photos when no business photo exists`() {
        val fixture = fixture()
        expectSearch(fixture.server, MATCHING_SEARCH_RESPONSE)
        expectPhotos(
            fixture.server,
            """[{"placeId":"123","photoType":"visitor","originalUrl":"https://naver.example/review.jpg"}]""",
        )

        assertNull(fixture.provider.fetch(REQUEST))
        assertEquals(emptyList(), fixture.storage.stored)
        fixture.server.verify()
    }

    @Test
    fun `missing token skips both actors`() {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = ApifyNaverPlacePhotoProvider(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = ApifyNaverPlacePhotoProperties(apiToken = ""),
            mediaStorage = FakeStorage(),
        )

        assertNull(provider.fetch(REQUEST))
        server.verify()
    }

    private fun expectSearch(server: MockRestServiceServer, response: String) {
        server.expect(requestTo(containsString("/v2/acts/search-actor/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().json(SEARCH_INPUT))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
    }

    private fun expectPhotos(server: MockRestServiceServer, response: String) {
        server.expect(requestTo(containsString("/v2/acts/photo-actor/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().json(PHOTO_INPUT))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
    }

    private fun fixture(): Fixture {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val storage = FakeStorage()
        return Fixture(
            provider = ApifyNaverPlacePhotoProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = ApifyNaverPlacePhotoProperties(
                    apiToken = "test-token",
                    searchActorId = "search-actor",
                    photoActorId = "photo-actor",
                ),
                mediaStorage = storage,
            ),
            server = server,
            storage = storage,
        )
    }

    private class FakeStorage : PostMediaStoragePort {
        val stored = mutableListOf<PostMedia>()

        @Synchronized
        override fun store(media: PostMedia): PostMedia {
            stored += media
            return media.copy(url = "https://cdn.example/${media.sequence}.jpg")
        }
    }

    private data class Fixture(
        val provider: ApifyNaverPlacePhotoProvider,
        val server: MockRestServiceServer,
        val storage: FakeStorage,
    )

    private companion object {
        const val SEARCH_INPUT =
            """{"keywords":["누크 카페 서울 강남구 테헤란로 1"],"scrapePlaceDetails":false,"maxResultsPerKeyword":5}"""
        const val PHOTO_INPUT =
            """{"placeUrls":[{"url":"https://map.naver.com/p/entry/place/123"}],"maxPhotos":3,"filterBy":"business","includeFilters":false}"""
        val MATCHING_SEARCH_RESPONSE = """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 1",
              "Latitude":"37.5001","Longitude":"127.0001","PlaceId":"123",
              "NaverMapUrl":"https://map.naver.com/p/entry/place/123",
              "SearchKeyword":"누크 카페 서울 강남구 테헤란로 1"}]
        """.trimIndent()
        val PHOTO_RESPONSE = """
            [
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/1.jpg"},
              {"placeId":"123","photoType":"visitor","originalUrl":"https://naver.example/review.jpg"},
              {"placeId":"999","photoType":"ibu","originalUrl":"https://naver.example/wrong.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/2.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/3.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/4.jpg"}
            ]
        """.trimIndent()
        val REQUEST = PlaceThumbnailProvider.Request(
            place = PlaceCandidate(
                provider = "KAKAO",
                externalPlaceId = "kakao-id",
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
