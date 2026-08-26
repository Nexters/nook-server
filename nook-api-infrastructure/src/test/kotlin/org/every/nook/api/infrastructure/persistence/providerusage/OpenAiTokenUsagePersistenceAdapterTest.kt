package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.OpenAiTokenUsagePeriod
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAiTokenUsagePersistenceAdapterTest {
    @Test
    fun `aggregates daily and feature model token usage in Seoul time`() {
        val repository = mock(OpenAiTokenUsageJpaRepository::class.java)
        `when`(
            repository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                Instant.parse("2026-07-31T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z"),
            ),
        ).thenReturn(
            listOf(
                event("place_clues", "gpt-5.4", 100, 40, 20, "2026-08-01T01:00:00Z"),
                event("place_clues", "gpt-5.4", 80, 0, 10, "2026-08-01T16:00:00Z"),
                event("image_text_extraction", "gpt-5.4-mini", 200, 100, 30, "2026-08-02T03:00:00Z"),
            ),
        )

        val overview = OpenAiTokenUsagePersistenceAdapter(repository).get(
            OpenAiTokenUsagePeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01")),
        )

        assertEquals(380, overview.inputTokens)
        assertEquals(140, overview.cachedInputTokens)
        assertEquals(60, overview.outputTokens)
        assertEquals(440, overview.totalTokens)
        assertEquals(listOf("2026-08-01", "2026-08-02"), overview.daily.map { it.date.toString() })
        assertEquals(2, overview.breakdowns.single { it.feature == "place_clues" }.requests)
        assertEquals(210, overview.breakdowns.single { it.feature == "place_clues" }.totalTokens)
    }

    private fun event(feature: String, model: String, input: Long, cached: Long, output: Long, at: String) =
        OpenAiTokenUsageEntity(
            feature = feature,
            model = model,
            inputTokens = input,
            cachedInputTokens = cached,
            outputTokens = output,
            totalTokens = input + output,
            occurredAt = Instant.parse(at),
        )
}
