package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GooglePlacePhotoProviderTest {
    @Test
    @Suppress("LongMethod")
    fun `fetches details for matched place and stores up to three Google place photos`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Goog-Api-Key", "google-key"))
            .andExpect(
                header(
                    "X-Goog-FieldMask",
                    org.hamcrest.Matchers.not(containsString("places.regularOpeningHours")),
                ),
            )
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "id":"google-place-id",
                      "displayName":{"text":"원동미나리삼겹살"},
                      "formattedAddress":"서울 용산구 한강대로77길 4-1",
                      "location":{"latitude":37.1,"longitude":127.1},
                      "photos":[{"name":"places/google-place/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/google-place-id")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Goog-FieldMask", containsString("regularOpeningHours")))
            .andRespond(
                withSuccess(
                    """
                    {
                        "id": "google-place-id",
                        "displayName": {"text": "원동미나리삼겹살"},
                        "formattedAddress": "서울 용산구 한강대로77길 4-1",
                        "location": {"latitude": 37.1, "longitude": 127.1},
                        "timeZone": {"id": "Asia/Seoul"},
                        "regularOpeningHours": {
                          "periods": [{
                            "open": {"day": 1, "hour": 10, "minute": 0},
                            "close": {"day": 1, "hour": 22, "minute": 0}
                          }],
                          "weekdayDescriptions": ["월요일: 오전 10:00~오후 10:00"]
                        },
                        "photos": [
                          {"name":"places/google-place/photos/photo-1"},
                          {"name":"places/google-place/photos/photo-2"},
                          {"name":"places/google-place/photos/photo-3"},
                          {"name":"places/google-place/photos/photo-4"},
                          {"name":"places/google-place/photos/photo-5"},
                          {"name":"places/google-place/photos/photo-6"},
                          {"name":"places/google-place/photos/photo-7"}
                        ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        (1..3).forEach { index ->
            fixture.server.expect(requestTo(containsString("/v1/places/google-place/photos/photo-$index/media")))
                .andExpect(requestTo(containsString("maxWidthPx=640")))
                .andExpect(requestTo(containsString("skipHttpRedirect=true")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "google-key"))
                .andRespond(
                    withSuccess(
                        """{"photoUri":"https://lh3.googleusercontent.com/photo-$index"}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )
        }

        val result = fixture.provider.fetch(candidate())

        assertEquals(3, result?.photoUrls?.size)
        assertEquals("google-place-id", result?.googlePlaceId)
        assertEquals("Asia/Seoul", result?.openingHours?.timeZone)
        assertEquals(1, result?.openingHours?.periods?.single()?.open?.day)
        assertEquals((0..2).toList(), fixture.storage.captured.map(PostMedia::sequence))
        fixture.server.verify()
    }

    @Test
    fun `returns null when Google request fails`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
            .andRespond(withServerError())

        assertNull(fixture.provider.fetch(candidate()))
    }

    @Test
    fun `recovers Google place id from resource name when id field is absent`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "name":"places/fallback-place-id",
                      "displayName":{"text":"원동미나리삼겹살"},
                      "formattedAddress":"서울 용산구 한강대로77길 4-1",
                      "location":{"latitude":37.1,"longitude":127.1},
                      "photos":[]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/fallback-place-id")))
            .andRespond(
                withSuccess(
                    """{"name":"places/fallback-place-id","photos":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.provider.fetch(candidate())

        assertEquals("fallback-place-id", result?.googlePlaceId)
        fixture.server.verify()
    }

    @Test
    fun `missing API key does not call Google`() {
        val fixture = providerFixture(properties = GooglePlacePhotoProperties(apiKey = ""))

        assertNull(fixture.provider.fetch(candidate()))
    }

    @Test
    fun `uses stored Google place id without search requests`() {
        val fixture = providerFixture()
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places/stored-place-id")))
            .andRespond(
                withSuccess(
                    """{"id":"stored-place-id","photos":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.provider.fetch(candidate(googlePlaceId = "stored-place-id"))

        assertEquals("stored-place-id", result?.googlePlaceId)
        fixture.server.verify()
    }

    @Test
    fun `does not store supplement when Google result does not match candidate`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "displayName":{"text":"다른 장소"},
                      "location":{"latitude":35.0,"longitude":129.0},
                      "photos":[{"name":"places/other/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertNull(fixture.provider.fetch(candidate()))
        assertTrue(fixture.storage.captured.isEmpty())
        fixture.server.verify()
    }

    @Test
    fun `uses one text search and fetches details for matched candidate`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andExpect(content().string(containsString("모로코코 서울 용산구")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "id":"place-with-photo",
                      "displayName":{"text":"모로코코"},
                      "formattedAddress":"서울 용산구 녹사평대로 40",
                      "location":{"latitude":37.5349,"longitude":126.9870},
                      "photos":[{"name":"places/place-with-photo/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/place-with-photo")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id":"place-with-photo",
                      "displayName":{"text":"모로코코 카페"},
                      "formattedAddress":"서울 용산구 녹사평대로 40",
                      "location":{"latitude":37.5349,"longitude":126.9870},
                      "photos":[{"name":"places/place-with-photo/photos/photo-1"}]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(
            requestTo(containsString("/v1/places/place-with-photo/photos/photo-1/media")),
        ).andRespond(
            withSuccess(
                """{"photoUri":"https://lh3.googleusercontent.com/photo-1"}""",
                MediaType.APPLICATION_JSON,
            ),
        )

        val result = fixture.provider.fetch(
            candidate(
                name = "모로코코",
                address = "서울 용산구 녹사평대로 40",
                latitude = BigDecimal("37.5349"),
                longitude = BigDecimal("126.9870"),
                category = "카페",
            ),
        )

        assertEquals("place-with-photo", result?.googlePlaceId)
        assertEquals(1, result?.photoUrls?.size)
        fixture.server.verify()
    }

    @Test
    fun `avoids far away place even when names match`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "id":"wrong-region-place",
                      "displayName":{"text":"보니스피자 용산점"},
                      "formattedAddress":"부산 해운대구 달맞이길 10",
                      "primaryType":"restaurant",
                      "location":{"latitude":35.1587,"longitude":129.1604},
                      "photos":[{"name":"places/wrong-region-place/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))

        assertNull(
            fixture.provider.fetch(
                candidate(
                    name = "보니스피자 용산점",
                    address = "서울 용산구 신흥로3길 2",
                    latitude = BigDecimal("37.5418"),
                    longitude = BigDecimal("126.9876"),
                    category = "음식점",
                ),
            ),
        )
        assertTrue(fixture.storage.captured.isEmpty())
        fixture.server.verify()
    }

    @Test
    fun `uses nearby search when Google place name differs but address is close`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "id":"nearby-place-id",
                      "displayName":{"text":"청송함흥냉면"},
                      "formattedAddress":"서울특별시 서대문구 연희맛로 6",
                      "primaryType":"restaurant",
                      "location":{"latitude":37.5681,"longitude":126.9327},
                      "photos":[{"name":"places/nearby-place-id/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/nearby-place-id")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id":"nearby-place-id",
                      "displayName":{"text":"청송함흥냉면"},
                      "formattedAddress":"서울특별시 서대문구 연희맛로 6",
                      "location":{"latitude":37.5681,"longitude":126.9327},
                      "photos":[{"name":"places/nearby-place-id/photos/photo-1"}]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/nearby-place-id/photos/photo-1/media")))
            .andRespond(
                withSuccess(
                    """{"photoUri":"https://lh3.googleusercontent.com/nearby-photo-1"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.provider.fetch(
            candidate(
                name = "청송함흥냉면전문점 본관",
                address = "서울 서대문구 연희맛로 6",
                latitude = BigDecimal("37.5681"),
                longitude = BigDecimal("126.9327"),
                category = "음식점",
            ),
        )

        assertEquals("nearby-place-id", result?.googlePlaceId)
        assertEquals(1, result?.photoUrls?.size)
        fixture.server.verify()
    }

    @Test
    fun `ignores unrelated nearby place and falls back to text search`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchNearby")))
            .andRespond(
                withSuccess(
                    """
                    {"places":[{
                      "id":"wrong-nearby-place",
                      "displayName":{"text":"치킨사냥"},
                      "formattedAddress":"서울특별시 서대문구 연희동 135-3",
                      "primaryType":"restaurant",
                      "location":{"latitude":37.5682,"longitude":126.9328},
                      "photos":[{"name":"places/wrong-nearby-place/photos/photo-1"}]
                    }]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(ExpectedCount.once(), requestTo(containsString("/v1/places:searchText")))
            .andRespond(withSuccess("""{"places":[]}""", MediaType.APPLICATION_JSON))

        val result = fixture.provider.fetch(
            candidate(
                name = "청송함흥냉면전문점 본관",
                address = "서울 서대문구 연희맛로 6",
                latitude = BigDecimal("37.5681"),
                longitude = BigDecimal("126.9327"),
                category = "음식점",
            ),
        )

        assertNull(result)
        assertTrue(fixture.storage.captured.isEmpty())
        fixture.server.verify()
    }

    private fun providerFixture(
        properties: GooglePlacePhotoProperties = GooglePlacePhotoProperties(
            baseUrl = "https://places.google.test",
            apiKey = "google-key",
            maxWidthPx = 640,
        ),
    ): ProviderFixture {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build()
        val storage = FakeStorage()
        return ProviderFixture(
            provider = GooglePlacePhotoProvider(builder.build(), properties, storage),
            server = server,
            storage = storage,
        )
    }

    private fun candidate(
        name: String = "원동미나리삼겹살",
        address: String = "서울 용산구 한강대로77길 4-1",
        latitude: BigDecimal = BigDecimal("37.1"),
        longitude: BigDecimal = BigDecimal("127.1"),
        category: String? = "음식점",
        googlePlaceId: String? = null,
    ): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
        phoneNumber = null,
        providerUrl = null,
        googlePlaceId = googlePlaceId,
    )

    private class FakeStorage : PostMediaStoragePort {
        val captured = mutableListOf<PostMedia>()

        override fun store(media: PostMedia): PostMedia {
            captured += media
            return media.copy(url = "https://cdn.example.com/google-place-${media.sequence}.jpg")
        }
    }

    private data class ProviderFixture(
        val provider: GooglePlacePhotoProvider,
        val server: org.springframework.test.web.client.MockRestServiceServer,
        val storage: FakeStorage,
    )
}
