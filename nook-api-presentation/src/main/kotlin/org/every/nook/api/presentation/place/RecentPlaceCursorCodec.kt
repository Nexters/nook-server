package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.PlaceSort
import org.every.nook.api.application.place.RecentPlaceCursor
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object RecentPlaceCursorCodec {
    fun encode(cursor: RecentPlaceCursor): String {
        val legacy = "${cursor.bookmarkedAt.epochSecond}:${cursor.bookmarkedAt.nano}:${cursor.bookmarkId}"
        val raw = if (cursor.sort == PlaceSort.LATEST) {
            legacy
        } else {
            listOf(
                legacy,
                cursor.sort.name,
                cursor.latitude?.toPlainString().orEmpty(),
                cursor.longitude?.toPlainString().orEmpty(),
                cursor.distanceScore?.toString().orEmpty(),
            ).joinToString(":")
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String?): RecentPlaceCursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(':')
            require(parts.size == CURSOR_PART_COUNT || parts.size == SORTED_CURSOR_PART_COUNT)
            val bookmarkedAt = Instant.ofEpochSecond(parts[0].toLong(), parts[1].toLong())
            val bookmarkId = parts[2].toLong()
            require(bookmarkId > 0)
            if (parts.size == CURSOR_PART_COUNT) {
                RecentPlaceCursor(bookmarkedAt, bookmarkId)
            } else {
                RecentPlaceCursor(
                    bookmarkedAt = bookmarkedAt,
                    bookmarkId = bookmarkId,
                    sort = PlaceSort.valueOf(parts[SORT_INDEX]),
                    latitude = parts[LATITUDE_INDEX].takeIf(String::isNotEmpty)?.let(::BigDecimal),
                    longitude = parts[LONGITUDE_INDEX].takeIf(String::isNotEmpty)?.let(::BigDecimal),
                    distanceScore = parts[DISTANCE_INDEX].takeIf(String::isNotEmpty)?.toDouble(),
                )
            }
        }.getOrElse {
            throw IllegalArgumentException("Recent place cursor is invalid", it)
        }
    }

    private const val CURSOR_PART_COUNT = 3
    private const val SORTED_CURSOR_PART_COUNT = 7
    private const val SORT_INDEX = 3
    private const val LATITUDE_INDEX = 4
    private const val LONGITUDE_INDEX = 5
    private const val DISTANCE_INDEX = 6
}
