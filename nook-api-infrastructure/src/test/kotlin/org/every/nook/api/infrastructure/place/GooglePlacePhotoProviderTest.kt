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

class GooglePlacePhotoProviderTest {
    @Test
    fun `fetches one Google place photo and stores it through media storage`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Goog-Api-Key", "google-key"))
            .andExpect(header("X-Goog-FieldMask", "places.photos.name"))
            .andRespond(
                withSuccess(
                    """{"places":[{"photos":[{"name":"places/google-place/photos/photo-1"}]}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        fixture.server.expect(requestTo(containsString("/v1/places/google-place/photos/photo-1/media")))
            .andExpect(requestTo(containsString("maxWidthPx=640")))
            .andExpect(requestTo(containsString("skipHttpRedirect=true")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Goog-Api-Key", "google-key"))
            .andRespond(
                withSuccess(
                    """{"photoUri":"https://lh3.googleusercontent.com/photo"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.provider.fetchThumbnailUrl(candidate())

        assertEquals("https://cdn.example.com/google-place.jpg", result)
        assertEquals("https://lh3.googleusercontent.com/photo", fixture.storage.captured?.url)
        fixture.server.verify()
    }

    @Test
    fun `returns null when Google request fails`() {
        val fixture = providerFixture()
        fixture.server.expect(requestTo(containsString("/v1/places:searchText")))
            .andRespond(withServerError())

        assertNull(fixture.provider.fetchThumbnailUrl(candidate()))
    }

    @Test
    fun `disabled provider does not call Google`() {
        val fixture = providerFixture(properties = GooglePlacePhotoProperties(enabled = false, apiKey = "google-key"))

        assertNull(fixture.provider.fetchThumbnailUrl(candidate()))
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
        var captured: PostMedia? = null

        override fun store(media: PostMedia): PostMedia {
            captured = media
            return media.copy(url = "https://cdn.example.com/google-place.jpg")
        }
    }

    private data class ProviderFixture(
        val provider: GooglePlacePhotoProvider,
        val server: org.springframework.test.web.client.MockRestServiceServer,
        val storage: FakeStorage,
    )
}
