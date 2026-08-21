package org.every.nook.api.application.post

import org.every.nook.api.application.place.ImageTranscript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `keeps a short collection headline from the body when no region is present`() {
        assertEquals(
            "느좋카페 10선",
            groundedPostTitle(
                body = "| 느좋카페 10선 \n\n에디터가 사랑하는 느낌 좋은 카페 모음집",
                inferredTitle = "Instagram 게시물",
            ),
        )
    }

    @Test
    fun `prefers an explicit body title over a cover title`() {
        assertEquals(
            "서울 카페 5곳",
            resolvePostTitle("서울 카페 5곳을 소개합니다", "표지의 다른 문구", "AI 제목"),
        )
    }

    @Test
    fun `rejects default null and image explanation titles`() {
        assertNull(resolvePostTitle("평범한 본문", "null null", "Instagram 게시물"))
        assertNull(
            resolvePostTitle(
                "평범한 본문",
                "표시된 날짜·회차 라벨이 여기에 와야 하지만 이미지는 회차 표기가 보이지 않습니다",
                "방문해보기 좋은 곳",
            ),
        )
    }

    @Test
    fun `rejects a cover title that was not present in OCR texts`() {
        val transcript = ImageTranscript(1, listOf("누구가 저장하고 다녀온 곳을 소개합니다"))

        assertNull(transcript.validatedCoverTitle(CoverTitleExtractor { "새로 만든 제목" }))
    }
}
