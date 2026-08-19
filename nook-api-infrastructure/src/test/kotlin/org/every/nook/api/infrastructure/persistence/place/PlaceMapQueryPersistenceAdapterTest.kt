package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.place.RecentPlaceCursor
import org.every.nook.api.domain.place.GeoBounds
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceMapQueryPersistenceAdapterTest {
    private val repository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = PlaceMapQueryPersistenceAdapter(repository)

    @Test
    fun `maps lightweight places inside the requested bounds`() {
        val row = mock(MapPlaceProjection::class.java)
        val bounds = GeoBounds(
            northLatitude = BigDecimal("37.6"),
            westLongitude = BigDecimal("126.8"),
            southLatitude = BigDecimal("37.4"),
            eastLongitude = BigDecimal("127.2"),
        )
        `when`(row.id).thenReturn(17)
        `when`(row.name).thenReturn("퍼머넌트해비탯")
        `when`(row.city).thenReturn("서울")
        `when`(row.category).thenReturn("음식점")
        `when`(row.latitude).thenReturn(BigDecimal("37.5"))
        `when`(row.longitude).thenReturn(BigDecimal("127.0"))
        `when`(row.color).thenReturn("BLUE")
        `when`(row.thumbnailParsingStatus).thenReturn("PROCESSING")
        `when`(
            repository.findMapPlaces(
                userId = 7,
                northLatitude = bounds.northLatitude,
                westLongitude = bounds.westLongitude,
                southLatitude = bounds.southLatitude,
                eastLongitude = bounds.eastLongitude,
            ),
        ).thenReturn(listOf(row))

        val result = adapter.findInBounds(7, bounds)

        assertEquals(17, result.single().id)
        assertEquals("퍼머넌트해비탯", result.single().name)
        assertEquals("서울", result.single().city)
        assertEquals("음식점", result.single().category)
        assertEquals(BigDecimal("37.5"), result.single().latitude)
        assertEquals("BLUE", result.single().color)
        assertEquals(PlaceThumbnailParsingStatusView.PROCESSING, result.single().thumbnailParsingStatus)
    }

    @Test
    fun `maps the yellow marker fallback returned for a place without a group color`() {
        val row = mock(MapPlaceProjection::class.java)
        val bounds = GeoBounds(
            northLatitude = BigDecimal("37.6"),
            westLongitude = BigDecimal("126.8"),
            southLatitude = BigDecimal("37.4"),
            eastLongitude = BigDecimal("127.2"),
        )
        `when`(row.id).thenReturn(17)
        `when`(row.name).thenReturn("퍼머넌트해비탯")
        `when`(row.latitude).thenReturn(BigDecimal("37.5"))
        `when`(row.longitude).thenReturn(BigDecimal("127.0"))
        `when`(row.color).thenReturn("YELLOW")
        `when`(
            repository.findMapPlaces(
                userId = 7,
                northLatitude = bounds.northLatitude,
                westLongitude = bounds.westLongitude,
                southLatitude = bounds.southLatitude,
                eastLongitude = bounds.eastLongitude,
            ),
        ).thenReturn(listOf(row))

        val result = adapter.findInBounds(7, bounds)

        assertEquals("YELLOW", result.single().color)
        assertEquals(PlaceThumbnailParsingStatusView.PENDING, result.single().thumbnailParsingStatus)
    }

    @Test
    fun `maps recent place projection and passes cursor`() {
        val bookmarkedAt = Instant.parse("2026-07-27T00:00:00Z")
        val cursor = RecentPlaceCursor(bookmarkedAt, 31)
        val row = mock(RecentPlaceProjection::class.java)
        `when`(row.bookmarkId).thenReturn(30)
        `when`(row.bookmarkedAt).thenReturn(bookmarkedAt.minusSeconds(1))
        `when`(row.placeId).thenReturn(17)
        `when`(row.name).thenReturn("퍼머넌트해비탯")
        `when`(row.city).thenReturn("용인")
        `when`(row.address).thenReturn("경기 용인시")
        `when`(row.category).thenReturn("카페")
        `when`(row.latitude).thenReturn(BigDecimal("37.5"))
        `when`(row.longitude).thenReturn(BigDecimal("127.0"))
        `when`(row.thumbnailUrl).thenReturn("https://example.com/place.jpg")
        `when`(row.thumbnailParsingStatus).thenReturn("PROCESSING")
        `when`(
            repository.findRecentPlaces(
                userId = 7,
                cursorBookmarkedAt = cursor.bookmarkedAt,
                cursorBookmarkId = cursor.bookmarkId,
                limit = 21,
            ),
        ).thenReturn(listOf(row))

        val result = adapter.findRecent(7, cursor, 21)

        assertEquals(17, result.single().id)
        assertEquals("용인", result.single().city)
        assertEquals("https://example.com/place.jpg", result.single().thumbnailUrl)
        assertEquals(PlaceThumbnailParsingStatusView.COMPLETED, result.single().thumbnailParsingStatus)
        verify(repository).findRecentPlaces(7, cursor.bookmarkedAt, cursor.bookmarkId, 21)
    }
}
