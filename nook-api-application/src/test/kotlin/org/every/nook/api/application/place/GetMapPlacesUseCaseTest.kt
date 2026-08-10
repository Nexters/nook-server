package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.domain.place.GeoBounds
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetMapPlacesUseCaseTest {
    @Test
    fun `queries map places with validated bounds`() {
        var capturedBounds: GeoBounds? = null
        val expected = listOf(
            MapPlaceView(
                id = 17,
                name = "퍼머넌트해비탯",
                city = "서울",
                latitude = BigDecimal("37.5"),
                longitude = BigDecimal("127.0"),
                color = "BLUE",
                thumbnailUrl = "https://example.com/map-place.jpg",
            ),
        )
        val useCase = GetMapPlacesUseCase(
            object : PlaceMapQueryPort {
                override fun findInBounds(userId: Long, bounds: GeoBounds): List<MapPlaceView> {
                    assertEquals(7, userId)
                    capturedBounds = bounds
                    return expected
                }

                override fun findRecent(userId: Long, cursor: RecentPlaceCursor?, limit: Int): List<RecentPlaceView> =
                    emptyList()
            },
        )

        val result = useCase(
            GetMapPlacesUseCase.Query(
                userId = 7,
                northLatitude = BigDecimal("37.6"),
                westLongitude = BigDecimal("126.8"),
                southLatitude = BigDecimal("37.4"),
                eastLongitude = BigDecimal("127.2"),
            ),
        )

        assertEquals(expected, result)
        assertEquals(BigDecimal("37.6"), capturedBounds?.northLatitude)
    }

    @Test
    fun `recent places request one extra row and creates a next cursor`() {
        val first = recentPlace(31, Instant.parse("2026-07-27T00:00:01Z"))
        val second = recentPlace(30, Instant.parse("2026-07-27T00:00:00Z"))
        val useCase = GetRecentPlacesUseCase(
            object : PlaceMapQueryPort {
                override fun findInBounds(userId: Long, bounds: GeoBounds): List<MapPlaceView> = emptyList()

                override fun findRecent(userId: Long, cursor: RecentPlaceCursor?, limit: Int): List<RecentPlaceView> {
                    assertEquals(3, limit)
                    return listOf(first, second, recentPlace(29, Instant.parse("2026-07-26T23:59:59Z")))
                }
            },
        )

        val result = useCase(GetRecentPlacesUseCase.Query(userId = 7, cursor = null, size = 2))

        assertEquals(listOf(first, second), result.items)
        assertEquals(RecentPlaceCursor(second.bookmarkedAt, second.bookmarkId), result.nextCursor)
        assertTrue(result.hasNext)
    }

    @Test
    fun `recent places omit a cursor on the last slice`() {
        val useCase = GetRecentPlacesUseCase(
            object : PlaceMapQueryPort {
                override fun findInBounds(userId: Long, bounds: GeoBounds): List<MapPlaceView> = emptyList()

                override fun findRecent(userId: Long, cursor: RecentPlaceCursor?, limit: Int): List<RecentPlaceView> =
                    listOf(recentPlace(31, Instant.parse("2026-07-27T00:00:00Z")))
            },
        )

        val result = useCase(GetRecentPlacesUseCase.Query(userId = 7, cursor = null, size = 2))

        assertFalse(result.hasNext)
        assertEquals(null, result.nextCursor)
    }

    private fun recentPlace(bookmarkId: Long, bookmarkedAt: Instant): RecentPlaceView = RecentPlaceView(
        bookmarkId = bookmarkId,
        bookmarkedAt = bookmarkedAt,
        id = bookmarkId,
        name = "장소",
        city = "서울",
        address = "서울",
        category = null,
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        thumbnailUrl = null,
    )
}
