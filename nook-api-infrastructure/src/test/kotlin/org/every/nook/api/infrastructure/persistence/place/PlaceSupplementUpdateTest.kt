package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceSupplementUpdateTest {
    @Test
    fun `replaces the obsolete fixed thumbnail with the post image`() {
        val place = place(OBSOLETE_FIXED_URL)

        place.updateSupplement(
            PlaceSupplement(
                openingHours = null,
                photoUrls = listOf(POST_IMAGE_URL),
                replaceThumbnailUrl = OBSOLETE_FIXED_URL,
            ),
        )

        assertEquals(POST_IMAGE_URL, place.thumbnailUrl)
    }

    @Test
    fun `keeps an existing non-fallback thumbnail`() {
        val place = place(EXISTING_PLACE_URL)

        place.updateSupplement(
            PlaceSupplement(
                openingHours = null,
                photoUrls = listOf(POST_IMAGE_URL),
                replaceThumbnailUrl = OBSOLETE_FIXED_URL,
            ),
        )

        assertEquals(EXISTING_PLACE_URL, place.thumbnailUrl)
    }

    private fun place(thumbnailUrl: String) = PlaceEntity(
        provider = "KAKAO",
        externalPlaceId = "123",
        name = "Nook Cafe",
        address = "서울",
        latitude = BigDecimal("37.1"),
        longitude = BigDecimal("127.1"),
        category = null,
        phoneNumber = null,
        thumbnailUrl = thumbnailUrl,
        thumbnailParsingStatus = PlaceThumbnailParsingStatus.COMPLETED,
    )

    private companion object {
        const val OBSOLETE_FIXED_URL = "https://cdn.example/fixed.jpg"
        const val POST_IMAGE_URL = "https://cdn.example/post-image.jpg"
        const val EXISTING_PLACE_URL = "https://cdn.example/existing-place.jpg"
    }
}
