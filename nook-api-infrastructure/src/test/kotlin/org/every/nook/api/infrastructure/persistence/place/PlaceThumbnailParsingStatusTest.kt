package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceThumbnailParsingStatusTest {
    @Test
    fun `thumbnail URL takes precedence over a stale pending status`() {
        val place = place(
            thumbnailUrl = "https://cdn.example.com/place.jpg",
            status = PlaceThumbnailParsingStatus.PENDING,
        )

        assertEquals(PlaceThumbnailParsingStatus.COMPLETED, place.effectiveThumbnailParsingStatus())
    }

    @Test
    fun `place without a thumbnail URL keeps its stored processing status`() {
        val place = place(
            thumbnailUrl = null,
            status = PlaceThumbnailParsingStatus.PROCESSING,
        )

        assertEquals(PlaceThumbnailParsingStatus.PROCESSING, place.effectiveThumbnailParsingStatus())
    }

    private fun place(thumbnailUrl: String?, status: PlaceThumbnailParsingStatus): PlaceEntity = PlaceEntity(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = "카페 악토버",
        address = "경기 성남시 분당구 동판교로52번길 9",
        latitude = BigDecimal("37.3870823"),
        longitude = BigDecimal("127.1145232"),
        thumbnailUrl = thumbnailUrl,
        thumbnailParsingStatus = status,
    )
}
