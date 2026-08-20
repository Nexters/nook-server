package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceClueGroundingTest {
    @Test
    fun `does not ground a station clue that exists only in the Instagram location tag`() {
        val clue = PlaceClue("건대입구역 7호선", "광진구", listOf("건대입구역 7호선"))

        assertFalse(
            clue.isGroundedIn(
                body = "#엘씨오\n서울 광진구 능동로7길 24",
                hashtags = listOf("엘씨오"),
            ),
        )
    }

    @Test
    fun `grounds the business clue from the body and hashtags without the location tag`() {
        val clue = PlaceClue(
            "엘씨오",
            "광진구",
            listOf("광진구 엘씨오"),
            addressHint = "서울 광진구 능동로7길 24",
        )

        assertTrue(
            clue.isGroundedIn(
                body = "#엘씨오\n서울 광진구 능동로7길 24",
                hashtags = listOf("엘씨오"),
            ),
        )
    }

    @Test
    fun `does not ground a regional clue from a collection location tag`() {
        val clue = PlaceClue("광진구", null, listOf("광진구"))

        assertFalse(clue.isGroundedIn(body = "카페 7곳", hashtags = emptyList()))
    }
}
