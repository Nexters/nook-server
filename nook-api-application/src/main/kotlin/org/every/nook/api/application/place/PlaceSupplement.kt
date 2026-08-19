package org.every.nook.api.application.place

import java.time.Instant
import java.time.ZoneId

data class PlaceSupplement(
    val openingHours: PlaceOpeningHours?,
    val photoUrls: List<String>,
    val googlePlaceId: String? = null,
    val replaceThumbnailUrl: String? = null,
) {
    init {
        require(photoUrls.size <= MAX_PHOTO_COUNT) { "Place photos must not exceed $MAX_PHOTO_COUNT" }
    }

    companion object {
        const val MAX_PHOTO_COUNT = 6
    }
}

data class PlaceOpeningHours(
    val timeZone: String = "",
    val periods: List<PlaceOpeningPeriod> = emptyList(),
    val weekdayDescriptions: List<String> = emptyList(),
) {
    fun isOpenAt(instant: Instant): Boolean {
        val local = instant.atZone(ZoneId.of(timeZone))
        val minuteOfWeek = local.dayOfWeek.value % DAYS_PER_WEEK * MINUTES_PER_DAY +
            local.hour * MINUTES_PER_HOUR + local.minute
        return periods.any { it.contains(minuteOfWeek) }
    }

    private fun PlaceOpeningPeriod.contains(minuteOfWeek: Int): Boolean {
        val start = open.minuteOfWeek()
        val end = close?.minuteOfWeek() ?: (start + MINUTES_PER_WEEK)
        return when {
            end > start -> minuteOfWeek in start until end
            end < start -> minuteOfWeek >= start || minuteOfWeek < end
            else -> true
        }
    }

    private fun PlaceOpeningPoint.minuteOfWeek(): Int = day * MINUTES_PER_DAY + hour * MINUTES_PER_HOUR + minute

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        const val MINUTES_PER_WEEK = DAYS_PER_WEEK * MINUTES_PER_DAY
    }
}

data class PlaceOpeningPeriod(val open: PlaceOpeningPoint = PlaceOpeningPoint(), val close: PlaceOpeningPoint? = null)

data class PlaceOpeningPoint(val day: Int = 0, val hour: Int = 0, val minute: Int = 0) {
    init {
        require(day in MIN_DAY..MAX_DAY) { "Opening day must be between $MIN_DAY and $MAX_DAY" }
        require(hour in MIN_HOUR..MAX_HOUR) { "Opening hour must be between $MIN_HOUR and $MAX_HOUR" }
        require(minute in MIN_MINUTE..MAX_MINUTE) {
            "Opening minute must be between $MIN_MINUTE and $MAX_MINUTE"
        }
    }

    private companion object {
        const val MIN_DAY = 0
        const val MAX_DAY = 6
        const val MIN_HOUR = 0
        const val MAX_HOUR = 23
        const val MIN_MINUTE = 0
        const val MAX_MINUTE = 59
    }
}
