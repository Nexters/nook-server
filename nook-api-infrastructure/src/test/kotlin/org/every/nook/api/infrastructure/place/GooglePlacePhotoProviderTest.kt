package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
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
    fun `fetches opening hours and stores up to six Google place photos`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Goog-Api-Key", "google-key"))
            .andExpect(header("X-Goog-FieldMask", containsString("places.regularOpeningHours")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "places": [{
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
                      }]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        (1..6).forEach { index ->
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

        assertEquals(6, result?.photoUrls?.size)
        assertEquals("google-place-id", result?.googlePlaceId)
        assertEquals("Asia/Seoul", result?.openingHours?.timeZone)
        assertEquals(1, result?.openingHours?.periods?.single()?.open?.day)
        assertEquals((0..5).toList(), fixture.storage.captured.map(PostMedia::sequence))
        fixture.server.verify()
    }

    @Test
    fun `returns null when Google request fails`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
            .andRespond(withServerError())

        assertNull(fixture.provider.fetch(candidate()))
    }

    @Test
    fun `recovers Google place id from resource name when id field is absent`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
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

        val result = fixture.provider.fetch(candidate())

        assertEquals("fallback-place-id", result?.googlePlaceId)
        fixture.server.verify()
    }

    @Test
    fun `disabled provider does not call Google`() {
        val fixture = providerFixture(properties = GooglePlacePhotoProperties(enabled = false, apiKey = "google-key"))

        assertNull(fixture.provider.fetch(candidate()))
    }

    @Test
    fun `does not store supplement when Google result does not match candidate`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
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

    private fun providerFixture(
        properties: GooglePlacePhotoProperties = GooglePlacePhotoProperties(
            enabled = true,
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

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = "원동미나리삼겹살",
        address = "서울 용산구 한강대로77길 4-1",
        latitude = BigDecimal("37.1"),
        longitude = BigDecimal("127.1"),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
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
