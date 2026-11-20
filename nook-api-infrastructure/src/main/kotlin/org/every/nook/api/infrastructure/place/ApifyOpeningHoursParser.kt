package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceOpeningPeriod
import org.every.nook.api.application.place.PlaceOpeningPoint
import tools.jackson.databind.JsonNode
import java.time.DayOfWeek
import java.util.Locale

internal object ApifyOpeningHoursParser {
    // The Actor has no IANA timezone field. Only KR has an unambiguous supported timezone here.
    fun parse(place: JsonNode): PlaceOpeningHours? = weeklyEntries(place)?.let { entries ->
        val periods = entries.map { entry ->
            parseDay(requireNotNull(dayNumber(entry.path("day").asString())), entry.path("hours").asString())
        }
        if (periods.any { it == null }) {
            null
        } else {
            PlaceOpeningHours(
                timeZone = "Asia/Seoul",
                periods = periods.filterNotNull().flatten(),
                weekdayDescriptions = entries.map { "${it.path("day").asString()}: ${it.path("hours").asString()}" },
            )
        }
    }

    private fun weeklyEntries(place: JsonNode): List<JsonNode>? {
        if (place.path("countryCode").asString() != "KR") return null
        val entries = place.path("openingHours").takeIf(JsonNode::isArray)?.toList() ?: return null
        val days = entries.map { dayNumber(it.path("day").asString()) }
        return entries.takeIf { days.toSet() == (0 until DAYS_PER_WEEK).toSet() && it.size == DAYS_PER_WEEK }
    }

    private fun dayNumber(day: String): Int? = DayOfWeek.entries.firstOrNull {
        it.name.equals(day, ignoreCase = true) || KOREAN_DAYS[it.value % DAYS_PER_WEEK] == day
    }?.let { it.value % DAYS_PER_WEEK }

    private fun parseDay(day: Int, hours: String): List<PlaceOpeningPeriod>? {
        val normalized = hours.replace(SPACE_PATTERN, " ").trim().lowercase(Locale.ROOT)
        return when (normalized) {
            "closed", "휴무일", "휴무", "영업 종료" -> emptyList()

            "open 24 hours", "24시간 영업" -> listOf(period(day, 0, MINUTES_PER_DAY))

            else -> normalized.split(',').map { parseRange(day, it) }.let { periods ->
                if (periods.any { it == null }) null else periods.filterNotNull()
            }
        }
    }

    private fun parseRange(day: Int, range: String): PlaceOpeningPeriod? {
        val ends = range.trim().split(RANGE_SEPARATOR)
        if (ends.size != 2 || ends.map { MERIDIEM_PATTERN.containsMatchIn(it) }.distinct().size != 1) return null
        val start = parseTime(ends[0]) ?: return null
        return parseTime(ends[1])?.takeUnless { end -> start == end || start == MINUTES_PER_DAY }?.let { end ->
            // Equal endpoints without an explicit 24-hour label are ambiguous.
            period(day, start, if (end < start) end + MINUTES_PER_DAY else end)
        }
    }

    private fun parseTime(text: String): Int? {
        val match = TIME_PATTERN.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[2].toInt()
        val minute = match.groupValues[3].ifEmpty { "0" }.toInt()
        val marker = (match.groupValues[1] + match.groupValues[4]).trim()
        val valid = minute in 0 until MINUTES_PER_HOUR && when {
            marker.isEmpty() -> hour in 0..HOURS_PER_DAY && (hour < HOURS_PER_DAY || minute == 0)
            else -> hour in 1..HALF_DAY_HOURS && marker in setOf("오전", "오후", "am", "pm")
        }
        if (!valid) return null
        val adjusted = if (marker.isEmpty()) {
            hour
        } else {
            hour % HALF_DAY_HOURS + if (marker == "오후" || marker == "pm") HALF_DAY_HOURS else 0
        }
        return adjusted * MINUTES_PER_HOUR + minute
    }

    private fun period(day: Int, start: Int, end: Int) = PlaceOpeningPeriod(
        open = point(day, start),
        close = point(day, end),
    )

    private fun point(day: Int, minutes: Int) = PlaceOpeningPoint(
        day = (day + minutes / MINUTES_PER_DAY) % DAYS_PER_WEEK,
        hour = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR,
        minute = minutes % MINUTES_PER_HOUR,
    )

    private const val DAYS_PER_WEEK = 7
    private const val HOURS_PER_DAY = 24
    private const val HALF_DAY_HOURS = 12
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR
    private val KOREAN_DAYS = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")
    private val SPACE_PATTERN = Regex("[\\s\\p{Z}]+")
    private val RANGE_SEPARATOR = Regex("\\s*(?:~|–|—|-| to )\\s*")
    private val MERIDIEM_PATTERN = Regex("오전|오후|am|pm")
    private val TIME_PATTERN = Regex("(오전|오후)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
}
