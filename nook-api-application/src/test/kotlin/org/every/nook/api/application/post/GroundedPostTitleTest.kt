package org.every.nook.api.application.post

import kotlin.test.Test
import kotlin.test.assertEquals

class GroundedPostTitleTest {
    @Test
    fun `uses explicit region category and count from body instead of an ungrounded title`() {
        val title = groundedPostTitle(
            body = "감도 좋은 서울 카페 19곳. 정리하다보니 19장에 다 담기지 않을 만큼 많다.",
            inferredTitle = "Seoul Korea 외 맛집 0곳",
        )

        assertEquals("서울 카페 19곳", title)
    }

    @Test
    fun `keeps inferred title when body has no explicit collection title`() {
        assertEquals("연희동 Lodge190", groundedPostTitle("빙수를 먹으러 왔다", "연희동 Lodge190"))
    }
}
