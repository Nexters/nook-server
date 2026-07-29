package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.RecentPlaceCursor
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object RecentPlaceCursorCodec {
    fun encode(cursor: RecentPlaceCursor): String {
        val raw = "${cursor.bookmarkedAt.epochSecond}:${cursor.bookmarkedAt.nano}:${cursor.bookmarkId}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String?): RecentPlaceCursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = raw.split(':')
            require(parts.size == CURSOR_PART_COUNT)
            val bookmarkedAt = Instant.ofEpochSecond(parts[0].toLong(), parts[1].toLong())
            val bookmarkId = parts[2].toLong()
            require(bookmarkId > 0)
            RecentPlaceCursor(bookmarkedAt = bookmarkedAt, bookmarkId = bookmarkId)
        }.getOrElse {
            throw IllegalArgumentException("Recent place cursor is invalid", it)
        }
    }

    private const val CURSOR_PART_COUNT = 3
}
