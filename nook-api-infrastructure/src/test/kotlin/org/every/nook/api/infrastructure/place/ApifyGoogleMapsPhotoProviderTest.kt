package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    fun `matches a differently named place by the same road address and keeps Google place id`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(NOVEMBER_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(novemberRequest())

        assertEquals(6, result?.photoUrls?.size)
        assertEquals("google-november", result?.googlePlaceId)
        fixture.server.verify()
    }

    @Test
    fun `matches a differently named place when Google address includes floor details`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(FLOOR_DETAIL_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(
            request(
                googlePlaceId = null,
                name = "아사시",
                address = "서울 중구 을지로 130-1",
                latitude = "37.5661844",
                longitude = "126.9923597",
            ),
        )

        assertEquals("google-asasi", result?.googlePlaceId)
        assertEquals(listOf("https://cdn.example/1.jpg"), result?.photoUrls)
        fixture.server.verify()
    }

    @Test
    fun `rejects a nearby hotel when a short restaurant name and address do not match`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(NEARBY_HOTEL_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(
            request(
                googlePlaceId = null,
                name = "음",
                address = "충북 청주시 상당구 남사로102번길 8",
                category = "음식점",
                latitude = "36.6314129",
                longitude = "127.4864368",
            ),
        )

        assertNull(result)
        assertEquals(emptyList(), fixture.storage.stored)
    }

    @Test
    fun `selects the exact-address restaurant from multiple results for a short place name`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(SHORT_NAME_CANDIDATES_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(
            request(
                googlePlaceId = null,
                name = "음",
                address = "충북 청주시 상당구 남사로102번길 8",
                category = "음식점",
                latitude = "36.6314129",
                longitude = "127.4864368",
            ),
        )

        assertEquals("correct-restaurant", result?.googlePlaceId)
        assertEquals(listOf("https://cdn.example/1.jpg"), result?.photoUrls)
    }

    @Test
    fun `keeps a category conflict as a ranking signal when name and address match`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(EXACT_MATCH_WITH_CATEGORY_CONFLICT_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(
            request(
                googlePlaceId = null,
                name = "누크 카페",
                category = "음식점",
            ),
        )

        assertEquals("category-conflict", result?.googlePlaceId)
        assertEquals(listOf("https://cdn.example/1.jpg"), result?.photoUrls)
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

    @Test
    fun `stores returned photos with bounded concurrency`() {
        val storage = ConcurrentStorage(expectedConcurrency = 3)
        val fixture = fixture(storage, storageConcurrency = 3)
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON))

        val results = fixture.provider.fetchAll(listOf(request("google-1"), request(null, "다른 카페")))

        assertEquals(3, storage.maxActive.get())
        assertEquals(6, results[0]?.photoUrls?.size)
        assertEquals(1, results[1]?.photoUrls?.size)
    }

    @Test
    fun `measures Actor waiting and image storage separately`() {
        val metrics = RecordingMetrics()
        val fixture = fixture(metrics = metrics)
        fixture.server.expect(requestTo(containsString("/run-sync-get-dataset-items")))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON))

        fixture.provider.fetchAll(
            listOf(
                request("google-1").copy(sourcePostId = 11),
                request(null, "다른 카페").copy(sourcePostId = 11),
            ),
        )

        assertEquals(listOf("apify-actor", "image-store"), metrics.measurements.map { it.stage })
    }

    private fun fixture(
        storage: FakeStorage = FakeStorage(),
        storageConcurrency: Int = 6,
        metrics: ProcessingMetrics = NoOpProcessingMetrics,
    ): Fixture {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return Fixture(
            provider = ApifyGoogleMapsPhotoProvider(
                restClient = builder.build(),
                objectMapper = jacksonObjectMapper(),
                properties = ApifyGoogleMapsProperties(
                    apiToken = "test-token",
                    actorId = "test-actor",
                    storageConcurrency = storageConcurrency,
                ),
                mediaStorage = storage,
                metrics = metrics,
            ),
            server = server,
            storage = storage,
        )
    }

    private fun request(
        googlePlaceId: String?,
        name: String = "누크 카페",
        address: String = "서울 강남구 테헤란로 1",
        category: String? = null,
        latitude: String = "37.5000",
        longitude: String = "127.0000",
    ) = PlaceThumbnailProvider.Request(
        place = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = "place-id-$name",
            name = name,
            address = address,
            latitude = BigDecimal(latitude),
            longitude = BigDecimal(longitude),
            category = category,
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

    private open class FakeStorage : PostMediaStoragePort {
        val stored = mutableListOf<PostMedia>()

        @Synchronized
        override fun store(media: PostMedia): PostMedia {
            stored += media
            return media.copy(url = "https://cdn.example/${stored.size}.jpg")
        }
    }

    private class ConcurrentStorage(expectedConcurrency: Int) : FakeStorage() {
        private val ready = CountDownLatch(expectedConcurrency)
        private val active = AtomicInteger()
        val maxActive = AtomicInteger()

        override fun store(media: PostMedia): PostMedia {
            val current = active.incrementAndGet()
            maxActive.accumulateAndGet(current) { previous, next -> maxOf(previous, next) }
            ready.countDown()
            check(ready.await(3, TimeUnit.SECONDS)) { "photo storage did not run concurrently" }
            return try {
                super.store(media)
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private class RecordingMetrics : ProcessingMetrics {
        val measurements = mutableListOf<ProcessingMetrics.Measurement>()

        override fun record(measurement: ProcessingMetrics.Measurement) {
            measurements += measurement
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
            "maxCrawledPlacesPerSearch":5,"maxImages":6,"scrapePlaceDetailPage":true,
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
        val FLOOR_DETAIL_RESPONSE = """
            [{"placeId":"google-asasi","title":"ASASI",
            "address":"대한민국 서울특별시 중구 을지로 130-1 4층 401호",
            "location":{"lat":37.5661253,"lng":126.9922289},
            "imageUrls":["https://google.example/asasi.jpg"]}]
        """.trimIndent()
        val NEARBY_HOTEL_RESPONSE = """
            [{"placeId":"ChIJKao-p8wnZTURLz8je9GVNj8","title":"정감호텔","categoryName":"호텔",
            "address":"대한민국 충청북도 청주시 상당구 남사로80번길 3",
            "location":{"lat":36.631624,"lng":127.48404},
            "imageUrls":["https://google.example/hotel.jpg"]}]
        """.trimIndent()
        val SHORT_NAME_CANDIDATES_RESPONSE = """
            [{"placeId":"nearby-hotel","title":"정감호텔","categoryName":"호텔",
            "address":"대한민국 충청북도 청주시 상당구 남사로80번길 3",
            "location":{"lat":36.631624,"lng":127.48404},
            "imageUrls":["https://google.example/hotel.jpg"]},
            {"placeId":"correct-restaurant","title":"음","categoryName":"음식점",
            "address":"대한민국 충청북도 청주시 상당구 남사로102번길 8",
            "location":{"lat":36.6314129,"lng":127.4864368},
            "imageUrls":["https://google.example/restaurant.jpg"]}]
        """.trimIndent()
        val EXACT_MATCH_WITH_CATEGORY_CONFLICT_RESPONSE = """
            [{"placeId":"category-conflict","title":"누크 카페","categoryName":"호텔",
            "address":"대한민국 서울특별시 강남구 테헤란로 1",
            "location":{"lat":37.5001,"lng":127.0001},
            "imageUrls":["https://google.example/category-conflict.jpg"]}]
        """.trimIndent()
    }
}
