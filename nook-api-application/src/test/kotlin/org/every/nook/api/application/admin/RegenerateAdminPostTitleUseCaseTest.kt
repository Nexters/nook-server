package org.every.nook.api.application.admin

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.post.CoverTitleExtractor
import org.every.nook.api.application.post.PostTitleInference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RegenerateAdminPostTitleUseCaseTest {
    @Test
    fun `regenerates only the title from shared OCR and records the command`() {
        val port = RecordingPort()
        val useCase = RegenerateAdminPostTitleUseCase(
            port = port,
            imageTextExtractor = ImageTextExtractor { request ->
                assertEquals("https://example.com/cover.jpg", request.images.single().imageUrl)
                listOf(ImageTranscript(1, listOf("VOL 2", "성수 카페 5곳")))
            },
            coverTitleExtractor = CoverTitleExtractor { request ->
                assertEquals(listOf("VOL 2", "성수 카페 5곳"), request.texts)
                "성수 카페 5곳"
            },
            titleInference = PostTitleInference { "AI 제목" },
        )

        val result = useCase(command())

        assertEquals("성수 카페 5곳", result.title)
        assertEquals("성수 카페 5곳", port.updated?.title)
        assertEquals("잘못된 제목 교정", port.updated?.reason)
    }

    @Test
    fun `does not update the title when inference fails`() {
        val port = RecordingPort()
        val useCase = RegenerateAdminPostTitleUseCase(
            port = port,
            imageTextExtractor = ImageTextExtractor { emptyList() },
            coverTitleExtractor = CoverTitleExtractor { null },
            titleInference = PostTitleInference { error("provider failed") },
        )

        assertFailsWith<IllegalStateException> { useCase(command()) }
        assertNull(port.updated)
    }

    private fun command() = RegenerateAdminPostTitleUseCase.Command(
        postId = 1,
        actor = AdminActor("subject", "admin@nook.test"),
        reason = "잘못된 제목 교정",
        requestId = "request-1",
    )

    private class RecordingPort : AdminPostTitleRegenerationPort {
        var updated: AdminPostTitleRegenerationPort.UpdateCommand? = null

        override fun findSource(postId: Long) = AdminPostTitleRegenerationPort.Source(
            postId = postId,
            body = "본문",
            hashtags = listOf("성수카페"),
            sourceLocationTag = "성수",
            firstImageUrl = "https://example.com/cover.jpg",
        )

        override fun updateTitle(command: AdminPostTitleRegenerationPort.UpdateCommand): AdminPostDetail {
            updated = command
            return AdminPostDetail(
                id = command.postId,
                canonicalUrl = "https://instagram.com/p/1",
                authorIdentifier = null,
                title = command.title,
                body = "본문",
                sourceLocationTag = "성수",
                contentParsingStatus = "COMPLETED",
                contentParsingFailureReason = null,
                placeParsingStatus = "COMPLETED",
                placeParsingFailureReason = null,
                savedUserCount = 1,
                mappingReviewed = false,
            )
        }
    }
}
