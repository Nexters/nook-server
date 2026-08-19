package org.every.nook.api.application.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReplaceAdminPostPlacesUseCaseTest {
    @Test
    fun `passes validated correction command to persistence boundary`() {
        var captured: AdminPostPlaceCorrectionPort.ReplaceCommand? = null
        val useCase = ReplaceAdminPostPlacesUseCase(
            object : AdminPostPlaceCorrectionPort {
                override fun replace(command: AdminPostPlaceCorrectionPort.ReplaceCommand): AdminPostDetail {
                    captured = command
                    return detail(command.postId)
                }
            },
        )

        useCase(
            ReplaceAdminPostPlacesUseCase.Command(
                postId = 10,
                placeIds = listOf(3, 2),
                actor = AdminActor("subject", "operator@example.com"),
                reason = " 잘못 연결된 장소 교정 ",
                requestId = "request-id",
            ),
        )

        assertEquals(listOf(3L, 2L), captured?.placeIds)
        assertEquals("잘못 연결된 장소 교정", captured?.reason)
    }

    @Test
    fun `requires a correction reason`() {
        val useCase = ReplaceAdminPostPlacesUseCase(
            object : AdminPostPlaceCorrectionPort {
                override fun replace(command: AdminPostPlaceCorrectionPort.ReplaceCommand): AdminPostDetail =
                    detail(command.postId)
            },
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(
                ReplaceAdminPostPlacesUseCase.Command(
                    postId = 1,
                    placeIds = listOf(2),
                    actor = AdminActor("subject", "operator@example.com"),
                    reason = " ",
                    requestId = null,
                ),
            )
        }
    }

    private fun detail(postId: Long) = AdminPostDetail(
        id = postId,
        canonicalUrl = "https://example.com/post",
        authorIdentifier = null,
        title = null,
        body = null,
        sourceLocationTag = null,
        contentParsingStatus = "COMPLETED",
        contentParsingFailureReason = null,
        placeParsingStatus = "COMPLETED",
        placeParsingFailureReason = null,
        savedUserCount = 0,
        mappingReviewed = true,
        places = emptyList(),
    )
}
