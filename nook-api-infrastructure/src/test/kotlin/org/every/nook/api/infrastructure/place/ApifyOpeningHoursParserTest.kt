package org.every.nook.api.infrastructure.place

import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApifyOpeningHoursParserTest {
    @Test
    fun `supports breaks closed days and Saturday overnight into Sunday`() {
        val hours = assertNotNull(
            parse(
                listOf(
                    "휴무일",
                    "오전 10:00~오후 2:00, 오후 5:00~오후 9:00",
                    "10:00–21:00",
                    "10:00–21:00",
                    "10:00–21:00",
                    "10:00–24:00",
                    "오후 10:00~오전 2:00",
                ),
            ),
        )

        assertTrue(hours.isOpenAt(Instant.parse("2026-11-16T03:00:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-11-16T06:00:00Z")))
        assertTrue(hours.isOpenAt(Instant.parse("2026-11-16T09:00:00Z")))
        assertTrue(hours.isOpenAt(Instant.parse("2026-11-21T16:00:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-11-21T17:00:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-11-22T03:00:00Z")))
        assertEquals("월요일: 오전 10:00~오후 2:00, 오후 5:00~오후 9:00", hours.weekdayDescriptions[1])
    }

    @Test
    fun `supports English AM PM unicode spaces and explicit all day hours`() {
        val hours = assertNotNull(parse(List(7) { "11\u202fAM to 9:30\u202fPM" }, days = ENGLISH_DAYS))
        assertTrue(hours.isOpenAt(Instant.parse("2026-11-16T12:29:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-11-16T12:30:00Z")))
        val allDay = assertNotNull(parse(List(7) { "Open 24 hours" }, days = ENGLISH_DAYS))
        assertTrue(allDay.isOpenAt(Instant.parse("2026-11-21T15:00:00Z")))
        assertTrue(allDay.isOpenAt(Instant.parse("2026-11-21T14:59:00Z")))
        assertTrue(assertNotNull(parse(List(7) { "24시간 영업" })).isOpenAt(Instant.EPOCH))
        assertFalse(assertNotNull(parse(List(7) { "Closed" })).isOpenAt(Instant.EPOCH))
    }

    @Test
    fun `does not infer missing days timezone or unsupported time formats`() {
        assertNull(parse(List(6) { "10:00–21:00" }))
        assertNull(parse(List(7) { "10:00–21:00" }, country = "US"))
        assertNull(parse(List(7) { "10:00–21:00" }, country = ""))
        assertNull(parse(List(7) { "10:00–21:00" }, days = List(7) { "월요일" }))
        listOf(
            "예약제",
            "25:00–26:00",
            "10:70–21:00",
            "오후 13:00~오후 9:00",
            "10:00–10:00",
            "12 to 9:30 am",
            "오전 10:00~9:00",
        ).forEach {
            assertNull(parse(List(7) { _ -> it }), it)
        }
    }

    private fun parse(hours: List<String>, country: String = "KR", days: List<String> = KOREAN_DAYS) =
        ApifyOpeningHoursParser.parse(
            jacksonObjectMapper().valueToTree(
                mapOf(
                    "countryCode" to country,
                    "openingHours" to hours.zip(days).map { (time, day) ->
                        mapOf("day" to day, "hours" to time)
                    },
                ),
            ),
        )

    private companion object {
        val KOREAN_DAYS = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")
        val ENGLISH_DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }
}
