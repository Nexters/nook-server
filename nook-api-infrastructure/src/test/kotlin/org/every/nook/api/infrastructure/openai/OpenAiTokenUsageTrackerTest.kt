package org.every.nook.api.infrastructure.openai

import org.every.nook.api.application.providerusage.OpenAiTokenUsage
import org.every.nook.api.application.providerusage.OpenAiTokenUsageRecorder
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiTokenUsageTrackerTest {
    @Test
    fun `records official Responses API usage fields`() {
        val recorded = mutableListOf<OpenAiTokenUsage>()
        val tracker = OpenAiTokenUsageTracker(
            recorder = OpenAiTokenUsageRecorder(recorded::add),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )

        tracker.record(
            jacksonObjectMapper().readTree(
                """
                {"model":"gpt-5.4-2026-08-01","usage":{"input_tokens":120,
                "input_tokens_details":{"cached_tokens":40},"output_tokens":30,"total_tokens":150}}
                """.trimIndent(),
            ),
            feature = "place_clues",
            fallbackModel = "gpt-fallback",
        )

        assertEquals(
            OpenAiTokenUsage("place_clues", "gpt-5.4-2026-08-01", 120, 40, 30, 150, NOW),
            recorded.single(),
        )
    }

    @Test
    fun `does not estimate usage when usage is absent`() {
        val recorded = mutableListOf<OpenAiTokenUsage>()
        val tracker = OpenAiTokenUsageTracker(OpenAiTokenUsageRecorder(recorded::add))

        tracker.record(jacksonObjectMapper().readTree("""{"model":"gpt-5.4"}"""), "place_clues", "fallback")

        assertTrue(recorded.isEmpty())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-27T01:02:03Z")
    }
}
