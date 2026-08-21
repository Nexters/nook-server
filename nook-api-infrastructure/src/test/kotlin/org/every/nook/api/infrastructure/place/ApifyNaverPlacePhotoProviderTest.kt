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
    fun `prioritizes representative images and fills up to six photos from all types`() {
        val fixture = fixture()
        expectSearch(fixture.server, MATCHING_SEARCH_RESPONSE)
        expectPhotos(fixture.server, PHOTO_RESPONSE)

        val result = fixture.provider.fetch(REQUEST)

        assertEquals(
            (0..5).map { "https://cdn.example/$it.jpg" },
            result?.photoUrls,
        )
        assertEquals(
            listOf(
                "https://naver.example/1.jpg",
                "https://naver.example/2.jpg",
                "https://naver.example/3.jpg",
                "https://naver.example/blog.jpg",
                "https://naver.example/representative.jpg",
                "https://naver.example/review.jpg",
            ),
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
              "SearchKeyword":"누크 카페 강남구"}]
            """.trimIndent(),
        )

        assertNull(fixture.provider.fetch(REQUEST))
        assertEquals(emptyList(), fixture.storage.stored)
        fixture.server.verify()
    }

    @Test
    fun `falls back to name and district within the same search actor run`() {
        val fixture = fixture()
        expectSearch(
            fixture.server,
            """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 1",
              "Latitude":"37.5001","Longitude":"127.0001","PlaceId":"123",
              "NaverMapUrl":"https://map.naver.com/p/entry/place/123",
              "Images":["https://naver.example/representative.jpg"],
              "SearchKeyword":"누크 카페 강남구"}]
            """.trimIndent(),
        )
        expectPhotos(fixture.server, PHOTO_RESPONSE)

        assertEquals((0..5).map { "https://cdn.example/$it.jpg" }, fixture.provider.fetch(REQUEST)?.photoUrls)
        fixture.server.verify()
    }

    @Test
    fun `requires both exact road address and nearby coordinates`() {
        val fixture = fixture()
        expectSearch(
            fixture.server,
            """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 10",
              "Latitude":"37.5001","Longitude":"127.0001","PlaceId":"999",
              "NaverMapUrl":"https://map.naver.com/p/entry/place/999",
              "SearchKeyword":"누크 카페 강남구"}]
            """.trimIndent(),
        )

        assertNull(fixture.provider.fetch(REQUEST))
        fixture.server.verify()
    }

    @Test
    fun `uses visitor photos when no representative image exists`() {
        val fixture = fixture()
        expectSearch(fixture.server, MATCHING_SEARCH_RESPONSE_WITHOUT_IMAGES)
        expectPhotos(
            fixture.server,
            """[{"placeId":"123","photoType":"visitor","originalUrl":"https://naver.example/review.jpg"}]""",
            PHOTO_INPUT_WITHOUT_REPRESENTATIVE,
        )

        assertEquals(listOf("https://cdn.example/0.jpg"), fixture.provider.fetch(REQUEST)?.photoUrls)
        assertEquals(listOf("https://naver.example/review.jpg"), fixture.storage.stored.map(PostMedia::url))
        fixture.server.verify()
    }

    @Test
    fun `skips photo actor when search returns six representative images`() {
        val fixture = fixture()
        expectSearch(fixture.server, SIX_REPRESENTATIVE_IMAGES_RESPONSE)

        val result = fixture.provider.fetch(REQUEST)

        assertEquals((0..5).map { "https://cdn.example/$it.jpg" }, result?.photoUrls)
        assertEquals(
            (1..6).map { "https://naver.example/representative-$it.jpg" },
            fixture.storage.stored.map(PostMedia::url).sorted(),
        )
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

    private fun expectPhotos(server: MockRestServiceServer, response: String, input: String = PHOTO_INPUT) {
        server.expect(requestTo(containsString("/v2/acts/photo-actor/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().json(input))
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
            """{"keywords":["누크 카페 테헤란로 1","누크 카페 강남구"],"scrapePlaceDetails":false,"maxResultsPerKeyword":5}"""
        const val PHOTO_INPUT =
            """{"placeUrls":[{"url":"https://map.naver.com/p/entry/place/123"}],"maxPhotos":5,"filterBy":"all","includeFilters":false}"""
        const val PHOTO_INPUT_WITHOUT_REPRESENTATIVE =
            """{"placeUrls":[{"url":"https://map.naver.com/p/entry/place/123"}],"maxPhotos":6,"filterBy":"all","includeFilters":false}"""
        val MATCHING_SEARCH_RESPONSE = """
            [{"Name":"누크 카페","FullAddress":"서울특별시 강남구 테헤란로 1",
              "Latitude":"37.5001","Longitude":"127.0001","PlaceId":"123",
              "NaverMapUrl":"https://map.naver.com/p/entry/place/123",
              "Images":["https://naver.example/representative.jpg"],
              "SearchKeyword":"누크 카페 테헤란로 1"}]
        """.trimIndent()
        val MATCHING_SEARCH_RESPONSE_WITHOUT_IMAGES = MATCHING_SEARCH_RESPONSE.replace(
            """"Images":["https://naver.example/representative.jpg"],""",
            "",
        )
        val SIX_REPRESENTATIVE_IMAGES_RESPONSE = MATCHING_SEARCH_RESPONSE.replace(
            """["https://naver.example/representative.jpg"]""",
            (1..6).joinToString(prefix = "[", postfix = "]") { "\"https://naver.example/representative-$it.jpg\"" },
        )
        val PHOTO_RESPONSE = """
            [
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/representative.jpg"},
              {"placeId":"123","photoType":"visitor","originalUrl":"https://naver.example/review.jpg"},
              {"placeId":"123","photoType":"ugc","originalUrl":"https://naver.example/blog.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/1.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/2.jpg"},
              {"placeId":"123","photoType":"ibu","originalUrl":"https://naver.example/3.jpg"},
              {"placeId":"123","photoType":"clip","mediaType":"video",
               "originalUrl":"https://naver.example/clip.jpg"},
              {"placeId":"999","photoType":"ibu","originalUrl":"https://naver.example/wrong.jpg"}
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
