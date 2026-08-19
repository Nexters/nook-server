package org.every.nook.api.application.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateAdminPostUseCaseTest {
    @Test
    fun `normalizes public post data and passes audit context`() {
        var captured: AdminPostCorrectionPort.UpdateCommand? = null
        val useCase = UpdateAdminPostUseCase(
            object : AdminPostCorrectionPort {
                override fun update(command: AdminPostCorrectionPort.UpdateCommand): AdminPostDetail {
                    captured = command
                    return detail(command.postId)
                }
            },
        )

        useCase(
            UpdateAdminPostUseCase.Command(
                postId = 1,
                authorIdentifier = " nook ",
                title = " title ",
                body = " body ",
                sourceLocationTag = " seoul ",
                hashtags = listOf(" cafe ", "cafe"),
                media = listOf(AdminPostMedia("IMAGE", "https://example.com/1.jpg", 0)),
                actor = AdminActor("subject", "admin@example.com"),
                reason = " correction ",
                requestId = "request-id",
            ),
        )

        assertEquals("nook", captured?.authorIdentifier)
        assertEquals(listOf("cafe"), captured?.hashtags)
        assertEquals("correction", captured?.reason)
    }

    @Test
    fun `requires a correction reason`() {
        val useCase = UpdateAdminPostUseCase(object : AdminPostCorrectionPort {
            override fun update(command: AdminPostCorrectionPort.UpdateCommand) = detail(command.postId)
        })

        assertFailsWith<IllegalArgumentException> {
            useCase(
                UpdateAdminPostUseCase.Command(
                    1,
                    null,
                    null,
                    null,
                    null,
                    emptyList(),
                    emptyList(),
                    AdminActor("subject", "admin@example.com"),
                    " ",
                    null,
                ),
            )
        }
    }

    private fun detail(id: Long) = AdminPostDetail(
        id = id,
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
        mappingReviewed = false,
    )
}
