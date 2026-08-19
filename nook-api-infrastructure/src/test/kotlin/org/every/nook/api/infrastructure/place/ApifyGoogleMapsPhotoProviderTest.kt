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

class ApifyGoogleMapsPhotoProviderTest {
    @Test
    fun `batches places and stores up to six photos matched by Google place id`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/v2/acts/test-actor/run-sync-get-dataset-items")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().json(INPUT))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON))

        val results = fixture.provider.fetchAll(listOf(request("google-1"), request(null, "다른 카페")))

        assertEquals(6, results[0]?.photoUrls?.size)
        assertEquals("google-1", results[0]?.googlePlaceId)
        assertEquals(1, results[1]?.photoUrls?.size)
        assertEquals(7, fixture.storage.stored.size)
        fixture.server.verify()
    }

    @Test
    fun `matches a differently named place by nearby coordinates and keeps Google place id`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(NOVEMBER_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(novemberRequest())

        assertEquals(6, result?.photoUrls?.size)
        assertEquals("google-november", result?.googlePlaceId)
        fixture.server.verify()
    }

    @Test
    fun `rejects an unrelated search result`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(UNRELATED_RESPONSE, MediaType.APPLICATION_JSON))

        assertNull(fixture.provider.fetch(request(null)))
        assertEquals(emptyList(), fixture.storage.stored)
    }

    @Test
    fun `does not treat an empty Actor address as an address match`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(EMPTY_ADDRESS_FAR_RESPONSE, MediaType.APPLICATION_JSON))

        assertNull(fixture.provider.fetch(request(null)))
        assertEquals(emptyList(), fixture.storage.stored)
    }

    @Test
    fun `missing token skips Actor`() {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = ApifyGoogleMapsPhotoProvider(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = ApifyGoogleMapsProperties(apiToken = ""),
            mediaStorage = FakeStorage(),
        )

        assertNull(provider.fetch(request(null)))
        server.verify()
    }

    private fun fixture(): Fixture {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val storage = FakeStorage()
        return Fixture(
            provider = ApifyGoogleMapsPhotoProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = ApifyGoogleMapsProperties(apiToken = "test-token", actorId = "test-actor"),
                mediaStorage = storage,
            ),
            server = server,
            storage = storage,
        )
    }

    private fun request(googlePlaceId: String?, name: String = "누크 카페") = PlaceThumbnailProvider.Request(
        place = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = "place-id-$name",
            name = name,
            address = "서울 강남구 테헤란로 1",
            latitude = BigDecimal("37.5000"),
            longitude = BigDecimal("127.0000"),
            category = null,
            phoneNumber = null,
            providerUrl = null,
            googlePlaceId = googlePlaceId,
        ),
    )

    private fun novemberRequest() = PlaceThumbnailProvider.Request(
        place = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = "1362430493",
            name = "더노벰버라운지 강남역KG타워점",
            address = "서울 강남구 테헤란로5길 7",
            latitude = BigDecimal("37.4992654"),
            longitude = BigDecimal("127.0292786"),
            category = null,
            phoneNumber = null,
            providerUrl = null,
        ),
    )

    private class FakeStorage : PostMediaStoragePort {
        val stored = mutableListOf<PostMedia>()

        override fun store(media: PostMedia): PostMedia {
            stored += media
            return media.copy(url = "https://cdn.example/${stored.size}.jpg")
        }
    }

    private data class Fixture(
        val provider: ApifyGoogleMapsPhotoProvider,
        val server: MockRestServiceServer,
        val storage: FakeStorage,
    )

    private companion object {
        const val INPUT = """
            {"searchStringsArray":["place_id:google-1","다른 카페 서울 강남구 테헤란로 1"],
            "maxCrawledPlacesPerSearch":1,"maxImages":6,"scrapePlaceDetailPage":true,
            "scrapeImageAuthors":false,"language":"ko"}
        """
        val RESPONSE = """
            [{"placeId":"google-1","title":"누크 카페","address":"서울 강남구 테헤란로 1",
            "location":{"lat":37.5001,"lng":127.0001},
            "imageUrls":["https://google.example/1.jpg","https://google.example/2.jpg",
            "https://google.example/3.jpg","https://google.example/4.jpg","https://google.example/5.jpg",
            "https://google.example/6.jpg","https://google.example/7.jpg"]},
            {"placeId":"google-2","title":"다른 카페","address":"서울 강남구 테헤란로 1",
            "location":{"lat":37.5002,"lng":127.0002},"imageUrls":["https://google.example/8.jpg"]}]
        """.trimIndent()
        val UNRELATED_RESPONSE = """
            [{"placeId":"wrong","title":"부산 식당","address":"부산 해운대구 해운대로 1",
            "location":{"lat":35.1587,"lng":129.1604},"imageUrls":["https://google.example/wrong.jpg"]}]
        """.trimIndent()
        val EMPTY_ADDRESS_FAR_RESPONSE = """
            [{"placeId":"wrong","title":"누크 카페","address":"",
            "location":{"lat":35.1587,"lng":129.1604},"imageUrls":["https://google.example/wrong.jpg"]}]
        """.trimIndent()
        val NOVEMBER_RESPONSE = """
            [{"placeId":"google-november","title":"The november 라운지 강남역 KG타워점",
            "address":"대한민국 서울특별시 강남구 테헤란로5길 7",
            "location":{"lat":37.4992654,"lng":127.0292786},
            "imageUrls":["https://google.example/1.jpg","https://google.example/2.jpg",
            "https://google.example/3.jpg","https://google.example/4.jpg",
            "https://google.example/5.jpg","https://google.example/6.jpg"]}]
        """.trimIndent()
    }
}
